#include <jni.h>
#include <dlfcn.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <string>
#include <map>
#include <vector>
#include <mutex>
#include <atomic>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <zlib.h>
#include "libretro.h"
#include "frame_buffer.h"
#include "native_audio.h"
#include "cheat_engine.h"

#define LOG_TAG "LibretroBridge"
static void bridge_log(const char *level_str, int prio, const char *fmt, ...) __attribute__((format(printf, 3, 4)));
#define LOGI(...) bridge_log("INFO",  ANDROID_LOG_INFO,  __VA_ARGS__)
#define LOGW(...) bridge_log("WARN",  ANDROID_LOG_WARN,  __VA_ARGS__)
#define LOGE(...) bridge_log("ERROR", ANDROID_LOG_ERROR, __VA_ARGS__)

// Not declared by this project's trimmed libretro.h; typedefs follow the same
// pattern as the rest of the core API function pointers below.
typedef void (*retro_cheat_reset_t)(void);
typedef void (*retro_cheat_set_t)(unsigned, bool, const char *);

static struct {
    void *handle;
    retro_init_t init;
    retro_deinit_t deinit;
    retro_run_t run;
    retro_load_game_t load_game;
    retro_unload_game_t unload_game;
    retro_set_environment_t set_environment;
    retro_set_video_refresh_t set_video_refresh;
    retro_set_audio_sample_t set_audio_sample;
    retro_set_audio_sample_batch_t set_audio_sample_batch;
    retro_set_input_poll_t set_input_poll;
    retro_set_input_state_t set_input_state;
    retro_get_system_info_t get_system_info;
    retro_get_system_av_info_t get_system_av_info;
    retro_serialize_size_t serialize_size;
    retro_serialize_t serialize;
    retro_unserialize_t unserialize;
    retro_get_memory_data_t get_memory_data;
    retro_get_memory_size_t get_memory_size;
    retro_reset_t reset;
    retro_cheat_reset_t cheat_reset;
    retro_cheat_set_t cheat_set;
    retro_set_controller_port_device_t set_controller_port_device;
} core;

// State shared with callbacks
#define MAX_PORTS 4
static int16_t g_input_state[MAX_PORTS] = {0};
static int16_t g_analog_state[MAX_PORTS][2][2] = {{{0}}}; // [port][stick][axis]
static int16_t g_pointer_x = 0;
static int16_t g_pointer_y = 0;
static int16_t g_pointer_pressed = 0;
static unsigned g_pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;

// Frame buffer written by video callback, read by renderer
static std::mutex g_frame_mutex;
static uint8_t *g_frame_buf = nullptr;
static unsigned g_frame_width = 0;
static unsigned g_frame_height = 0;
static size_t g_frame_pitch = 0;
static bool g_frame_ready = false;

static JavaVM *g_jvm = nullptr;

// Paths
static char g_system_dir[512] = {0};
static char g_save_dir[512] = {0};

// Core options
struct CoreOptionValue {
    std::string value;
    std::string label;
};
struct CoreOption {
    std::string key;
    std::string desc;
    std::string info;
    std::string category;
    std::vector<CoreOptionValue> values;
    std::string selected;
};
struct CoreCategory {
    std::string key;
    std::string desc;
    std::string info;
};
static std::vector<CoreOption> g_core_options;
static std::vector<CoreCategory> g_core_categories;
static std::map<std::string, std::string> g_option_overrides;
static bool g_options_dirty = false;

// Disk control
static struct retro_disk_control_callback g_disk_control = {0};
static bool g_has_disk_control = false;
static const char *(*g_get_image_label)(unsigned index) = nullptr;

// Controller info
struct ControllerTypeInfo {
    std::string desc;
    unsigned id;
};
static std::vector<ControllerTypeInfo> g_controller_types[MAX_PORTS];

// Memory map (for RetroAchievements)
static struct retro_memory_map g_memory_map = {0};
static struct retro_memory_descriptor *g_memory_descriptors = nullptr;
static unsigned g_memory_descriptor_count = 0;

// --- Log ring buffer (shared by bridge_log and core_log) ---

static const int LOG_RING_SIZE = 64;
static std::string g_log_ring[LOG_RING_SIZE];
static int g_log_ring_head = 0;
static int g_log_ring_count = 0;
static std::mutex g_log_ring_mutex;

static void ring_push(const char *level_str, const char *fmt, va_list args) {
    char buf[512];
    vsnprintf(buf, sizeof(buf), fmt, args);
    size_t len = strlen(buf);
    if (len > 0 && buf[len - 1] == '\n') buf[len - 1] = '\0';

    std::lock_guard<std::mutex> lock(g_log_ring_mutex);
    std::string entry = std::string("[") + level_str + "] " + buf;
    g_log_ring[g_log_ring_head] = std::move(entry);
    g_log_ring_head = (g_log_ring_head + 1) % LOG_RING_SIZE;
    if (g_log_ring_count < LOG_RING_SIZE) g_log_ring_count++;
}

static void bridge_log(const char *level_str, int prio, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(prio, LOG_TAG, fmt, args);
    va_end(args);

    va_list args2;
    va_start(args2, fmt);
    ring_push(level_str, fmt, args2);
    va_end(args2);
}

// --- Libretro callbacks ---

static void core_log(enum retro_log_level level, const char *fmt, ...) {
    va_list args;
    va_start(args, fmt);
    int prio = ANDROID_LOG_DEBUG;
    const char *level_str = "DEBUG";
    switch (level) {
        case RETRO_LOG_DEBUG: prio = ANDROID_LOG_DEBUG; level_str = "DEBUG"; break;
        case RETRO_LOG_INFO:  prio = ANDROID_LOG_INFO;  level_str = "INFO";  break;
        case RETRO_LOG_WARN:  prio = ANDROID_LOG_WARN;  level_str = "WARN";  break;
        case RETRO_LOG_ERROR: prio = ANDROID_LOG_ERROR;  level_str = "ERROR"; break;
    }
    __android_log_vprint(prio, "LibretroCore", fmt, args);
    va_end(args);

    va_list args2;
    va_start(args2, fmt);
    ring_push(level_str, fmt, args2);
    va_end(args2);
}

static unsigned g_rotation = 0;

// --- A/V info reported through SET_GEOMETRY / SET_SYSTEM_AV_INFO ---
// Seeded from retro_get_system_av_info after load_game; geometry serial bumps
// on every change so the renderer can pick up new aspect ratios cheaply.
static struct retro_game_geometry g_geometry = {};
static struct retro_system_timing g_timing = {};
static bool g_geometry_valid = false;
static std::atomic<int> g_geometry_serial{0};

// --- Hardware rendering (RETRO_ENVIRONMENT_SET_HW_RENDER, GLES only) ---
// The core renders into a frontend-owned FBO. The FBO and context_reset must
// happen on the GL thread, so SET_HW_RENDER only records the request and
// nativeHwContextReset (called lazily by the renderer) does the GL work.
static struct retro_hw_render_callback g_hw_render = {};
static bool g_hw_requested = false;
static bool g_hw_context_alive = false;   // context_reset called, context_destroy not yet
static EGLContext g_hw_egl_ctx = EGL_NO_CONTEXT;
static std::atomic<unsigned> g_hw_fbo{0}; // read by get_current_framebuffer (any core thread)
static GLuint g_hw_tex = 0;
static GLuint g_hw_rb = 0;
static unsigned g_hw_fbo_w = 0;
static unsigned g_hw_fbo_h = 0;
static bool g_hw_es3 = false;
static GLint g_hw_max_attribs = 8;
static GLint g_hw_max_tex_units = 8;
static bool g_hw_resize_pending = false;
static bool g_in_core_run = false;
static void *g_gles_handle = nullptr;

static void hw_on_geometry_changed();

static bool environment_cb(unsigned cmd, void *data) {
    cmd &= ~0x10000U; /* strip RETRO_ENVIRONMENT_EXPERIMENTAL flag */
    switch (cmd) {
        case 1: /* RETRO_ENVIRONMENT_SET_ROTATION */
            g_rotation = *(const unsigned *)data & 3;
            return true;

        case RETRO_ENVIRONMENT_GET_OVERSCAN:
            *(bool *)data = true;
            return true;

        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *(bool *)data = true;
            return true;

        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
            return true;

        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return true;

        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *(unsigned *)data = 2;
            return true;

        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            g_pixel_format = *(const unsigned *)data;
            LOGI("Pixel format set to: %u", g_pixel_format);
            return true;

        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *(const char **)data = g_system_dir;
            return true;

        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *(const char **)data = g_save_dir;
            return true;

        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            auto *cb = (struct retro_log_callback *)data;
            cb->log = core_log;
            return true;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            g_core_options.clear();
            g_core_categories.clear();
            auto *vars = (const struct retro_variable *)data;
            while (vars && vars->key) {
                CoreOption opt;
                opt.key = vars->key;
                const char *desc = vars->value;
                const char *pipe = strchr(desc, ';');
                if (pipe) {
                    opt.desc = std::string(desc, pipe - desc);
                    const char *p = pipe + 1;
                    while (*p == ' ') p++;
                    std::string valstr(p);
                    size_t pos = 0;
                    while ((pos = valstr.find('|')) != std::string::npos) {
                        std::string v = valstr.substr(0, pos);
                        opt.values.push_back({v, v});
                        valstr.erase(0, pos + 1);
                    }
                    if (!valstr.empty()) opt.values.push_back({valstr, valstr});
                    if (!opt.values.empty()) {
                        if (opt.key == "melonds_touch_mode" || opt.key == "melondsds_touch_mode") {
                            opt.selected = "Touch";
                        } else {
                            opt.selected = opt.values[0].value;
                        }
                    }
                } else {
                    opt.desc = desc;
                }
                g_core_options.push_back(opt);
                vars++;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS: {
            g_core_options.clear();
            g_core_categories.clear();
            auto *def = (const struct retro_core_option_definition *)data;
            while (def && def->key) {
                CoreOption opt;
                opt.key = def->key;
                opt.desc = def->desc ? def->desc : def->key;
                opt.info = def->info ? def->info : "";
                for (int i = 0; i < 128 && def->values[i].value; i++) {
                    const char *label = def->values[i].label;
                    std::string lbl = (label && label[0]) ? label : def->values[i].value;
                    opt.values.push_back({def->values[i].value, lbl});
                }
                if (opt.key == "melonds_touch_mode" || opt.key == "melondsds_touch_mode") {
                    opt.selected = "Touch";
                } else {
                    opt.selected = def->default_value ? def->default_value :
                                   (!opt.values.empty() ? opt.values[0].value : "");
                }
                g_core_options.push_back(opt);
                def++;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_INTL: {
            auto *intl = (const struct retro_core_options_intl *)data;
            if (intl && intl->us) {
                environment_cb(RETRO_ENVIRONMENT_SET_CORE_OPTIONS, (void *)intl->us);
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_VARIABLE: {
            auto *var = (const struct retro_variable *)data;
            if (!var) return true;
            if (!var->key) return true;
            if (var->value) {
                g_option_overrides[var->key] = var->value;
                g_options_dirty = true;
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2: {
            g_core_options.clear();
            g_core_categories.clear();
            auto *opts = (const struct retro_core_options_v2 *)data;
            if (opts) {
                if (opts->categories) {
                    auto *cat = opts->categories;
                    while (cat->key) {
                        g_core_categories.push_back({
                            cat->key,
                            cat->desc ? cat->desc : cat->key,
                            cat->info ? cat->info : ""
                        });
                        cat++;
                    }
                }
                if (opts->definitions) {
                    auto *def = opts->definitions;
                    while (def->key) {
                        CoreOption opt;
                        opt.key = def->key;
                        opt.desc = (def->desc_categorized && def->desc_categorized[0])
                                   ? def->desc_categorized : (def->desc ? def->desc : def->key);
                        const char *info_src = (def->info_categorized && def->info_categorized[0])
                                   ? def->info_categorized : def->info;
                        opt.info = info_src ? info_src : "";
                        opt.category = def->category_key ? def->category_key : "";
                        for (int i = 0; i < 128 && def->values[i].value; i++) {
                            const char *label = def->values[i].label;
                            std::string lbl = (label && label[0]) ? label : def->values[i].value;
                            opt.values.push_back({def->values[i].value, lbl});
                        }
                        if (opt.key == "melonds_touch_mode" || opt.key == "melondsds_touch_mode") {
                            opt.selected = "Touch";
                        } else {
                            opt.selected = def->default_value ? def->default_value :
                                           (!opt.values.empty() ? opt.values[0].value : "");
                        }
                        g_core_options.push_back(opt);
                        def++;
                    }
                }
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2_INTL: {
            auto *intl = (const struct retro_core_options_v2_intl *)data;
            if (intl && intl->us) {
                // Reuse the v2 handler by faking the environment call
                environment_cb(RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2, (void *)intl->us);
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_UPDATE_DISPLAY_CALLBACK:
            return false;

        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto *var = (struct retro_variable *)data;
            if (!var || !var->key) return false;
            auto it = g_option_overrides.find(var->key);
            if (it != g_option_overrides.end()) {
                var->value = it->second.c_str();
                return true;
            }
            for (auto &opt : g_core_options) {
                if (opt.key == var->key) {
                    var->value = opt.selected.c_str();
                    return true;
                }
            }
            return false;
        }

        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *(bool *)data = g_options_dirty;
            g_options_dirty = false;
            return true;

        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            return true;

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_INTERFACE: {
            if (!data) {
                memset(&g_disk_control, 0, sizeof(g_disk_control));
                g_has_disk_control = false;
                g_get_image_label = nullptr;
                return true;
            }
            auto *cb = (struct retro_disk_control_callback *)data;
            g_disk_control = *cb;
            g_has_disk_control = true;
            g_get_image_label = nullptr;
            LOGI("Disk control interface set");
            return true;
        }

        case RETRO_ENVIRONMENT_SET_DISK_CONTROL_EXT_INTERFACE: {
            if (!data) {
                memset(&g_disk_control, 0, sizeof(g_disk_control));
                g_has_disk_control = false;
                g_get_image_label = nullptr;
                return true;
            }
            auto *cb = (struct retro_disk_control_ext_callback *)data;
            g_disk_control.set_eject_state = cb->set_eject_state;
            g_disk_control.get_eject_state = cb->get_eject_state;
            g_disk_control.get_image_index = cb->get_image_index;
            g_disk_control.set_image_index = cb->set_image_index;
            g_disk_control.get_num_images = cb->get_num_images;
            g_disk_control.replace_image_index = cb->replace_image_index;
            g_disk_control.add_image_index = cb->add_image_index;
            g_has_disk_control = true;
            g_get_image_label = cb->get_image_label;
            LOGI("Disk control ext interface set");
            return true;
        }

        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *(unsigned *)data = 0; // RETRO_LANGUAGE_ENGLISH
            return true;

        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO: {
            auto *info = (const struct retro_controller_info *)data;
            for (int p = 0; p < MAX_PORTS && info[p].num_types > 0; p++) {
                g_controller_types[p].clear();
                for (unsigned t = 0; t < info[p].num_types; t++) {
                    const char *desc = info[p].types[t].desc;
                    if (!desc || !desc[0]) continue;
                    g_controller_types[p].push_back({desc, info[p].types[t].id});
                }
            }
            return true;
        }

        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS: {
            const struct retro_memory_map *mmap = (const struct retro_memory_map *)data;
            free(g_memory_descriptors);
            g_memory_descriptor_count = mmap->num_descriptors;
            g_memory_descriptors = (struct retro_memory_descriptor *)calloc(
                mmap->num_descriptors, sizeof(struct retro_memory_descriptor));
            memcpy(g_memory_descriptors, mmap->descriptors,
                mmap->num_descriptors * sizeof(struct retro_memory_descriptor));
            g_memory_map.descriptors = g_memory_descriptors;
            g_memory_map.num_descriptors = g_memory_descriptor_count;
            LOGI("Memory map set: %u descriptors", g_memory_descriptor_count);
            cheat_engine_invalidate_memory();
            return true;
        }

        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            auto *cb = (struct retro_hw_render_callback *)data;
            if (!cb) return false;
            switch (cb->context_type) {
                case RETRO_HW_CONTEXT_OPENGLES2:
                case RETRO_HW_CONTEXT_OPENGLES3:
                case RETRO_HW_CONTEXT_OPENGLES_VERSION:
                    break;
                default:
                    // Desktop GL / Vulkan / D3D: decline so the core falls back.
                    LOGW("SET_HW_RENDER: rejecting context type %d", (int)cb->context_type);
                    return false;
            }
            cb->get_current_framebuffer = [](void) -> uintptr_t { return (uintptr_t)g_hw_fbo.load(); };
            cb->get_proc_address = [](const char *sym) -> retro_proc_address_t {
                void *fn = (void *)eglGetProcAddress(sym);
                if (!fn) {
                    if (!g_gles_handle) g_gles_handle = dlopen("libGLESv3.so", RTLD_NOW);
                    if (g_gles_handle) fn = dlsym(g_gles_handle, sym);
                }
                return (retro_proc_address_t)fn;
            };
            g_hw_render = *cb;
            g_hw_requested = true;
            LOGI("SET_HW_RENDER: type=%d v%u.%u depth=%d stencil=%d bottom_left=%d",
                 (int)cb->context_type, cb->version_major, cb->version_minor,
                 cb->depth, cb->stencil, cb->bottom_left_origin);
            return true;
        }

        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
            // GLES3: PPSSPP's GLES build only takes its GL path for OPENGL/
            // OPENGLES3/DUMMY, so answering OPENGLES2 would push it to Vulkan.
            *(unsigned *)data = RETRO_HW_CONTEXT_OPENGLES3;
            return true;

        case RETRO_ENVIRONMENT_SET_HW_SHARED_CONTEXT:
            return true;

        case RETRO_ENVIRONMENT_SET_GEOMETRY: {
            auto *geom = (const struct retro_game_geometry *)data;
            if (!geom) return false;
            // Only base size and aspect may change here; max stays as-is.
            g_geometry.base_width = geom->base_width;
            g_geometry.base_height = geom->base_height;
            g_geometry.aspect_ratio = geom->aspect_ratio;
            g_geometry_valid = true;
            g_geometry_serial++;
            LOGI("SET_GEOMETRY: %ux%u aspect %.3f", geom->base_width, geom->base_height, geom->aspect_ratio);
            return true;
        }

        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            auto *av = (const struct retro_system_av_info *)data;
            if (!av) return false;
            g_geometry = av->geometry;
            g_timing = av->timing;
            g_geometry_valid = true;
            g_geometry_serial++;
            LOGI("SET_SYSTEM_AV_INFO: %ux%u (max %ux%u) aspect %.3f @ %.2f fps, %.0f Hz",
                 av->geometry.base_width, av->geometry.base_height,
                 av->geometry.max_width, av->geometry.max_height,
                 av->geometry.aspect_ratio, av->timing.fps, av->timing.sample_rate);
            hw_on_geometry_changed();
            return true;
        }

        default:
            LOGI("Unhandled env cmd: %u", cmd);
            return false;
    }
}

static void video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    if (data == RETRO_HW_FRAME_BUFFER_VALID) {
        // HW core rendered into g_hw_fbo; the renderer samples it directly.
        std::lock_guard<std::mutex> lock(g_frame_mutex);
        g_frame_width = width;
        g_frame_height = height;
        g_frame_ready = true;
        return;
    }
    if (!data) return;

    size_t bpp = (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) ? 4 : 2;
    size_t needed = width * height * bpp;

    std::lock_guard<std::mutex> lock(g_frame_mutex);

    if (!g_frame_buf || g_frame_width != width || g_frame_height != height) {
        free(g_frame_buf);
        g_frame_buf = (uint8_t *)malloc(needed);
        g_frame_width = width;
        g_frame_height = height;
    }

    const uint8_t *src = (const uint8_t *)data;
    uint8_t *dst = g_frame_buf;
    size_t row_bytes = width * bpp;

    if (g_pixel_format == RETRO_PIXEL_FORMAT_0RGB1555) {
        // Convert 0RGB1555 to RGB565 in-place so the renderer only needs two paths
        for (unsigned y = 0; y < height; y++) {
            const uint16_t *src16 = (const uint16_t *)src;
            uint16_t *dst16 = (uint16_t *)dst;
            for (unsigned x = 0; x < width; x++) {
                uint16_t px = src16[x];
                unsigned r = (px >> 10) & 0x1F;
                unsigned g = (px >> 5) & 0x1F;
                unsigned b = px & 0x1F;
                dst16[x] = (r << 11) | (((g << 1) | (g >> 4)) << 5) | b;
            }
            src += pitch;
            dst += row_bytes;
        }
    } else if (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) {
        // Convert XRGB8888 (0x00RRGGBB) to ABGR so GL_RGBA/GL_UNSIGNED_BYTE reads correctly
        for (unsigned y = 0; y < height; y++) {
            const uint32_t *src32 = (const uint32_t *)src;
            uint32_t *dst32 = (uint32_t *)dst;
            for (unsigned x = 0; x < width; x++) {
                uint32_t px = src32[x];
                uint32_t r = (px >> 16) & 0xFF;
                uint32_t g = (px >> 8) & 0xFF;
                uint32_t b = px & 0xFF;
                dst32[x] = 0xFF000000 | (b << 16) | (g << 8) | r;
            }
            src += pitch;
            dst += row_bytes;
        }
    } else {
        for (unsigned y = 0; y < height; y++) {
            memcpy(dst, src, row_bytes);
            src += pitch;
            dst += row_bytes;
        }
    }
    g_frame_pitch = row_bytes;
    g_frame_ready = true;
}

// --- HW render helpers (GL thread only) ---

// True when the GL context current on this thread is the one the core's
// context_reset ran against, i.e. g_hw_fbo and friends are valid names here.
static bool hw_context_current() {
    return g_hw_context_alive && g_hw_egl_ctx != EGL_NO_CONTEXT &&
           eglGetCurrentContext() == g_hw_egl_ctx;
}

// The core changes GL state freely; put back everything LibretroRenderer and
// ShaderPipeline assume (default FBO, no tests/blend, client-side attribs on
// VAO 0, no bound buffers/samplers, default pixel store, black clear color).
static void hw_restore_gl_state() {
    if (eglGetCurrentContext() == EGL_NO_CONTEXT) return;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindRenderbuffer(GL_RENDERBUFFER, 0);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_STENCIL_TEST);
    glDisable(GL_SCISSOR_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_CULL_FACE);
    glDisable(GL_POLYGON_OFFSET_FILL);
    glDisable(GL_SAMPLE_ALPHA_TO_COVERAGE);
    glDisable(GL_SAMPLE_COVERAGE);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glDepthMask(GL_TRUE);
    glStencilMask(0xFFFFFFFF);
    glBlendEquation(GL_FUNC_ADD);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glUseProgram(0);
    if (g_hw_es3) {
        glDisable(GL_RASTERIZER_DISCARD);
        glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
        glBindVertexArray(0);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_IMAGES, 0);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
    }
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    for (GLint i = 0; i < g_hw_max_attribs; i++) {
        glDisableVertexAttribArray((GLuint)i);
        if (g_hw_es3) glVertexAttribDivisor((GLuint)i, 0);
    }
    for (GLint u = 0; u < g_hw_max_tex_units; u++) {
        glActiveTexture(GL_TEXTURE0 + u);
        glBindTexture(GL_TEXTURE_2D, 0);
        if (g_hw_es3) glBindSampler((GLuint)u, 0);
    }
    glActiveTexture(GL_TEXTURE0);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glPixelStorei(GL_PACK_ALIGNMENT, 4);
}

static bool gl_has_extension(const char *name) {
    const char *ext = (const char *)glGetString(GL_EXTENSIONS);
    if (!ext) return false;
    size_t len = strlen(name);
    for (const char *p = strstr(ext, name); p; p = strstr(p + len, name)) {
        if ((p == ext || p[-1] == ' ') && (p[len] == ' ' || p[len] == '\0')) return true;
    }
    return false;
}

// (Re)allocate the color texture and depth/stencil storage of g_hw_fbo. The
// FBO name itself is kept so a core that cached get_current_framebuffer()
// keeps rendering into the right object after a resize. Must be called
// outside retro_run (it clobbers bindings) with the HW context current.
static bool hw_alloc_fbo(unsigned w, unsigned h) {
    if (w == 0) w = 1;
    if (h == 0) h = 1;
    GLuint fbo = g_hw_fbo.load();
    if (!fbo) glGenFramebuffers(1, &fbo);
    if (!g_hw_tex) glGenTextures(1, &g_hw_tex);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, g_hw_tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (g_hw_es3) glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, (GLsizei)w, (GLsizei)h, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);

    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, g_hw_tex, 0);

    if (g_hw_render.depth || g_hw_render.stencil) {
        if (!g_hw_rb) glGenRenderbuffers(1, &g_hw_rb);
        glBindRenderbuffer(GL_RENDERBUFFER, g_hw_rb);
        // Packed depth24/stencil8 is core in ES3 and GL_OES_packed_depth_stencil
        // in ES2 (same enum value).
        bool packed = g_hw_es3 || gl_has_extension("GL_OES_packed_depth_stencil");
        if (packed) {
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, (GLsizei)w, (GLsizei)h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, g_hw_rb);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g_hw_rb);
        } else if (g_hw_render.depth) {
            glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16, (GLsizei)w, (GLsizei)h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, g_hw_rb);
        } else {
            glRenderbufferStorage(GL_RENDERBUFFER, GL_STENCIL_INDEX8, (GLsizei)w, (GLsizei)h);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_STENCIL_ATTACHMENT, GL_RENDERBUFFER, g_hw_rb);
        }
        glBindRenderbuffer(GL_RENDERBUFFER, 0);
    }

    GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("HW FBO incomplete: 0x%x (%ux%u)", status, w, h);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        g_hw_fbo = fbo;
        return false;
    }

    glViewport(0, 0, (GLsizei)w, (GLsizei)h);
    glDisable(GL_SCISSOR_TEST);
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glDepthMask(GL_TRUE);
    glStencilMask(0xFFFFFFFF);
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    g_hw_fbo = fbo;
    g_hw_fbo_w = w;
    g_hw_fbo_h = h;
    LOGI("HW FBO %u allocated: %ux%u", fbo, w, h);
    return true;
}

// Size the FBO must have to hold any frame the core may produce.
static void hw_target_size(unsigned *w, unsigned *h) {
    unsigned tw = g_geometry_valid ? g_geometry.max_width : 0;
    unsigned th = g_geometry_valid ? g_geometry.max_height : 0;
    if (g_geometry_valid && g_geometry.base_width > tw) tw = g_geometry.base_width;
    if (g_geometry_valid && g_geometry.base_height > th) th = g_geometry.base_height;
    if (tw == 0) tw = 640;
    if (th == 0) th = 480;
    *w = tw;
    *h = th;
}

// Called from SET_SYSTEM_AV_INFO. Grow the FBO if max geometry outgrew it;
// inside retro_run the core's bindings must not be disturbed, so defer to
// the start of the next nativeRun.
static void hw_on_geometry_changed() {
    if (!g_hw_context_alive) return;  // context reset will size from g_geometry
    unsigned w, h;
    hw_target_size(&w, &h);
    if (w <= g_hw_fbo_w && h <= g_hw_fbo_h) return;
    if (!g_in_core_run && hw_context_current()) {
        hw_alloc_fbo(w > g_hw_fbo_w ? w : g_hw_fbo_w, h > g_hw_fbo_h ? h : g_hw_fbo_h);
        hw_restore_gl_state();
    } else {
        g_hw_resize_pending = true;
    }
}

// Call the core's context_destroy (once) and drop our GL objects. Objects
// are only deleted when their context is current; if the EGL context was
// lost they are already gone and the names may now belong to someone else.
static void hw_context_destroy() {
    if (!g_hw_context_alive) return;
    bool same_ctx = hw_context_current();
    g_hw_context_alive = false;
    if (g_hw_render.context_destroy) g_hw_render.context_destroy();
    if (same_ctx) {
        GLuint fbo = g_hw_fbo.load();
        if (fbo) glDeleteFramebuffers(1, &fbo);
        if (g_hw_tex) glDeleteTextures(1, &g_hw_tex);
        if (g_hw_rb) glDeleteRenderbuffers(1, &g_hw_rb);
    }
    if (eglGetCurrentContext() != EGL_NO_CONTEXT) hw_restore_gl_state();
    g_hw_fbo = 0;
    g_hw_tex = 0;
    g_hw_rb = 0;
    g_hw_fbo_w = 0;
    g_hw_fbo_h = 0;
    g_hw_resize_pending = false;
    g_hw_egl_ctx = EGL_NO_CONTEXT;
    LOGI("HW context destroyed");
}

// Forget all HW state without calling into the core (core unloaded/closed).
static void hw_reset_state() {
    g_hw_render = {};
    g_hw_requested = false;
    g_hw_context_alive = false;
    g_hw_egl_ctx = EGL_NO_CONTEXT;
    g_hw_fbo = 0;
    g_hw_tex = 0;
    g_hw_rb = 0;
    g_hw_fbo_w = 0;
    g_hw_fbo_h = 0;
    g_hw_resize_pending = false;
    g_in_core_run = false;
}

// Read the last HW frame (w x h, top-left of the FBO) into dst as RGBA bytes
// with the top row first and alpha forced opaque -- the same layout
// video_refresh_cb produces for XRGB8888 software frames.
static bool hw_read_frame(uint8_t *dst, size_t capacity, unsigned w, unsigned h) {
    if (!dst || w == 0 || h == 0) return false;
    if (!hw_context_current() || !g_hw_fbo.load()) return false;
    if (w > g_hw_fbo_w) w = g_hw_fbo_w;
    if (h > g_hw_fbo_h) h = g_hw_fbo_h;
    size_t row = (size_t)w * 4;
    if (capacity < row * h) return false;

    glBindFramebuffer(GL_FRAMEBUFFER, g_hw_fbo.load());
    if (g_hw_es3) {
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glPixelStorei(GL_PACK_ROW_LENGTH, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
    }
    glPixelStorei(GL_PACK_ALIGNMENT, 4);
    glReadPixels(0, 0, (GLsizei)w, (GLsizei)h, GL_RGBA, GL_UNSIGNED_BYTE, dst);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);

    if (g_hw_render.bottom_left_origin) {
        std::vector<uint8_t> tmp(row);
        for (unsigned y = 0; y < h / 2; y++) {
            uint8_t *a = dst + (size_t)y * row;
            uint8_t *b = dst + (size_t)(h - 1 - y) * row;
            memcpy(tmp.data(), a, row);
            memcpy(a, b, row);
            memcpy(b, tmp.data(), row);
        }
    }
    for (size_t i = 3; i < row * h; i += 4) dst[i] = 0xFF;
    return true;
}

static void audio_sample_cb(int16_t left, int16_t right) {
    int16_t buf[2] = {left, right};
    nativeAudioWrite(buf, 1);
}

static size_t audio_sample_batch_cb(const int16_t *data, size_t frames) {
    if (frames > 0) nativeAudioWrite(data, (int32_t)frames);
    return frames;
}

static void input_poll_cb(void) {
    // No-op - input state is set from Kotlin side before retro_run
}

static int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= MAX_PORTS) return 0;
    unsigned base = device & 0xFF;
    if (base == RETRO_DEVICE_JOYPAD) {
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) return g_input_state[port];
        return (g_input_state[port] >> id) & 1;
    }
    if (base == RETRO_DEVICE_ANALOG && index < 2 && id < 2) {
        return g_analog_state[port][index][id];
    }
    if (base == RETRO_DEVICE_POINTER && port == 0) {
        if (id == RETRO_DEVICE_ID_POINTER_X) return g_pointer_x;
        if (id == RETRO_DEVICE_ID_POINTER_Y) return g_pointer_y;
        if (id == RETRO_DEVICE_ID_POINTER_PRESSED) return g_pointer_pressed;
        if (id == RETRO_DEVICE_ID_POINTER_COUNT) return g_pointer_pressed ? 1 : 0;
    }
    return 0;
}

// --- JNI helpers ---

#define LOAD_SYM(name) do { \
    core.name = (retro_##name##_t)dlsym(core.handle, "retro_" #name); \
    if (!core.name) { LOGE("Missing symbol: retro_%s", #name); } \
} while(0)

// --- JNI exports ---

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeLoadCore(JNIEnv *env, jobject, jstring corePath) {
    // Full reset of all bridge state so each core load starts from a clean slate,
    // independent of whether the previous session's deinit ran.
    free(g_memory_descriptors);
    g_memory_descriptors = nullptr;
    g_memory_descriptor_count = 0;
    g_memory_map = {0};

    g_core_options.clear();
    g_core_categories.clear();
    g_option_overrides.clear();
    g_options_dirty = false;

    g_pixel_format = RETRO_PIXEL_FORMAT_0RGB1555;
    g_rotation = 0;

    hw_reset_state();
    g_geometry = {};
    g_timing = {};
    g_geometry_valid = false;

    memset(g_input_state, 0, sizeof(g_input_state));
    memset(g_analog_state, 0, sizeof(g_analog_state));

    {
        std::lock_guard<std::mutex> lock(g_frame_mutex);
        free(g_frame_buf);
        g_frame_buf = nullptr;
        g_frame_width = 0;
        g_frame_height = 0;
        g_frame_pitch = 0;
        g_frame_ready = false;
    }

    memset(&g_disk_control, 0, sizeof(g_disk_control));
    g_has_disk_control = false;
    g_get_image_label = nullptr;
    for (int p = 0; p < MAX_PORTS; p++) g_controller_types[p].clear();

    cheat_engine_clear();
    cheat_engine_invalidate_memory();

    const char *path = env->GetStringUTFChars(corePath, nullptr);
    core.handle = dlopen(path, RTLD_LAZY);
    env->ReleaseStringUTFChars(corePath, path);

    if (!core.handle) {
        LOGE("dlopen failed: %s", dlerror());
        return JNI_FALSE;
    }

    LOAD_SYM(init);
    LOAD_SYM(deinit);
    LOAD_SYM(run);
    LOAD_SYM(load_game);
    LOAD_SYM(unload_game);
    LOAD_SYM(set_environment);
    LOAD_SYM(set_video_refresh);
    LOAD_SYM(set_audio_sample);
    LOAD_SYM(set_audio_sample_batch);
    LOAD_SYM(set_input_poll);
    LOAD_SYM(set_input_state);
    LOAD_SYM(get_system_info);
    LOAD_SYM(get_system_av_info);
    LOAD_SYM(serialize_size);
    LOAD_SYM(serialize);
    LOAD_SYM(unserialize);
    LOAD_SYM(get_memory_data);
    LOAD_SYM(get_memory_size);
    LOAD_SYM(reset);
    LOAD_SYM(cheat_reset);
    LOAD_SYM(cheat_set);
    core.set_controller_port_device = (retro_set_controller_port_device_t)dlsym(core.handle, "retro_set_controller_port_device");

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeInit(JNIEnv *env, jobject,
        jstring systemDir, jstring saveDir) {
    const char *sys = env->GetStringUTFChars(systemDir, nullptr);
    const char *sav = env->GetStringUTFChars(saveDir, nullptr);
    strncpy(g_system_dir, sys, sizeof(g_system_dir) - 1);
    strncpy(g_save_dir, sav, sizeof(g_save_dir) - 1);
    env->ReleaseStringUTFChars(systemDir, sys);
    env->ReleaseStringUTFChars(saveDir, sav);

    g_rotation = 0;
    g_option_overrides.clear();
    g_options_dirty = false;

    core.set_environment(environment_cb);
    core.set_video_refresh(video_refresh_cb);
    core.set_audio_sample(audio_sample_cb);
    core.set_audio_sample_batch(audio_sample_batch_cb);
    core.set_input_poll(input_poll_cb);
    core.set_input_state(input_state_cb);
    core.init();
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioInit(JNIEnv *, jobject, jint sampleRate, jdouble contentFps) {
    nativeAudioInit(sampleRate, contentFps);
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioStop(JNIEnv *, jobject) {
    nativeAudioStop();
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioSetMuted(JNIEnv *, jobject, jboolean muted) {
    nativeAudioSetMuted(muted);
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioSetNonblock(JNIEnv *, jobject, jboolean nonblock) {
    nativeAudioSetNonblock(nonblock);
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioPause(JNIEnv *, jobject) {
    nativeAudioPause();
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioResume(JNIEnv *, jobject) {
    nativeAudioResume();
}

JNIEXPORT jstring JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeAudioGetDiagnostics(JNIEnv *env, jobject) {
    return env->NewStringUTF(nativeAudioGetDiagnostics());
}

JNIEXPORT jintArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeLoadGame(JNIEnv *env, jobject, jstring romPath) {
    const char *path = env->GetStringUTFChars(romPath, nullptr);

    struct retro_system_info sys_info = {0};
    core.get_system_info(&sys_info);

    void *rom_data = nullptr;
    long size = 0;

    if (!sys_info.need_fullpath) {
        FILE *f = fopen(path, "rb");
        if (!f) {
            LOGE("Failed to open ROM: %s", path);
            env->ReleaseStringUTFChars(romPath, path);
            return nullptr;
        }
        fseek(f, 0, SEEK_END);
        size = ftell(f);
        fseek(f, 0, SEEK_SET);
        rom_data = malloc(size);
        size_t read = fread(rom_data, 1, size, f);
        fclose(f);
        if (read != (size_t)size) {
            LOGE("nativeLoadGame: short read (%zu of %zu)", read, (size_t)size);
            free(rom_data);
            env->ReleaseStringUTFChars(romPath, path);
            return nullptr;
        }
    }

    struct retro_game_info game_info = {0};
    game_info.path = path;
    game_info.data = rom_data;
    game_info.size = size;

    bool ok = core.load_game(&game_info);
    free(rom_data);
    env->ReleaseStringUTFChars(romPath, path);

    if (!ok) {
        LOGE("retro_load_game failed");
        return nullptr;
    }

    if (!g_option_overrides.empty())
        g_options_dirty = true;

    struct retro_system_av_info av_info;
    core.get_system_av_info(&av_info);
    g_geometry = av_info.geometry;
    g_timing = av_info.timing;
    g_geometry_valid = true;
    g_geometry_serial++;

    LOGI("Game loaded: %ux%u @ %.2f fps, audio %.0f Hz",
         av_info.geometry.base_width, av_info.geometry.base_height,
         av_info.timing.fps, av_info.timing.sample_rate);

    cheat_engine_invalidate_memory();

    // Return [width, height, fps*1_000_000, sampleRate]
    jintArray result = env->NewIntArray(4);
    jint vals[4] = {
        (jint)av_info.geometry.base_width,
        (jint)av_info.geometry.base_height,
        (jint)(av_info.timing.fps * 1000000),
        (jint)av_info.timing.sample_rate
    };
    env->SetIntArrayRegion(result, 0, 4, vals);
    return result;
}

/* ra_integration.c */
extern "C" void ra_process_frame(void);

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeRun(JNIEnv *, jobject) {
    if (g_hw_requested) {
        // A HW core must not run before context_reset (nativeHwContextReset).
        if (!hw_context_current()) return;
        if (g_hw_resize_pending) {
            g_hw_resize_pending = false;
            unsigned w, h;
            hw_target_size(&w, &h);
            hw_alloc_fbo(w > g_hw_fbo_w ? w : g_hw_fbo_w, h > g_hw_fbo_h ? h : g_hw_fbo_h);
        }
        g_in_core_run = true;
        core.run();
        g_in_core_run = false;
        hw_restore_gl_state();
        cheat_engine_apply();
        ra_process_frame();
        return;
    }
    core.run();
    cheat_engine_apply();
    ra_process_frame();
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeIsHwRender(JNIEnv *, jobject) {
    return g_hw_requested ? JNI_TRUE : JNI_FALSE;
}

// GL thread. Allocates the FBO for the current EGL context and calls the
// core's context_reset. No-op (true) if already done for this context.
JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeHwContextReset(JNIEnv *, jobject) {
    if (!g_hw_requested || !core.handle) return JNI_FALSE;
    EGLContext cur = eglGetCurrentContext();
    if (cur == EGL_NO_CONTEXT) {
        LOGE("nativeHwContextReset: no current EGL context");
        return JNI_FALSE;
    }
    if (g_hw_context_alive) {
        if (cur == g_hw_egl_ctx) return JNI_TRUE;
        // EGL context was replaced underneath us: tell the core first.
        hw_context_destroy();
    }

    int major = 2;
    const char *ver = (const char *)glGetString(GL_VERSION);
    if (ver) {
        const char *p = strstr(ver, "OpenGL ES");
        if (p) {
            p += 9;
            while (*p && (*p < '0' || *p > '9')) p++;
            if (*p) major = atoi(p);
        }
    }
    g_hw_es3 = major >= 3;
    bool wants_es3 = g_hw_render.context_type == RETRO_HW_CONTEXT_OPENGLES3 ||
                     (g_hw_render.context_type == RETRO_HW_CONTEXT_OPENGLES_VERSION &&
                      g_hw_render.version_major >= 3);
    if (wants_es3 && !g_hw_es3)
        LOGW("Core asked for GLES %u.%u but context is %s", g_hw_render.version_major,
             g_hw_render.version_minor, ver ? ver : "?");

    g_hw_max_attribs = 8;
    glGetIntegerv(GL_MAX_VERTEX_ATTRIBS, &g_hw_max_attribs);
    if (g_hw_max_attribs <= 0 || g_hw_max_attribs > 32) g_hw_max_attribs = 16;
    g_hw_max_tex_units = 8;
    glGetIntegerv(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, &g_hw_max_tex_units);
    if (g_hw_max_tex_units <= 0 || g_hw_max_tex_units > 32) g_hw_max_tex_units = 16;

    unsigned w, h;
    hw_target_size(&w, &h);
    if (!hw_alloc_fbo(w, h)) {
        hw_restore_gl_state();
        return JNI_FALSE;
    }
    g_hw_egl_ctx = cur;
    g_hw_context_alive = true;
    g_hw_resize_pending = false;
    LOGI("HW context reset (%s)", ver ? ver : "?");
    if (g_hw_render.context_reset) g_hw_render.context_reset();
    hw_restore_gl_state();
    return JNI_TRUE;
}

// GL thread. Calls context_destroy and frees the FBO; no-op if not alive.
JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeHwContextDestroy(JNIEnv *, jobject) {
    hw_context_destroy();
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeHwContextAlive(JNIEnv *, jobject) {
    return g_hw_context_alive ? JNI_TRUE : JNI_FALSE;
}

// [colorTexture, fboWidth, fboHeight, bottomLeftOrigin]; zeros if no context.
JNIEXPORT jintArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeHwGetTarget(JNIEnv *env, jobject) {
    jint vals[4] = {0, 0, 0, 0};
    if (g_hw_context_alive) {
        vals[0] = (jint)g_hw_tex;
        vals[1] = (jint)g_hw_fbo_w;
        vals[2] = (jint)g_hw_fbo_h;
        vals[3] = g_hw_render.bottom_left_origin ? 1 : 0;
    }
    jintArray result = env->NewIntArray(4);
    env->SetIntArrayRegion(result, 0, 4, vals);
    return result;
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetGeometrySerial(JNIEnv *, jobject) {
    return (jint)g_geometry_serial.load();
}

// Latest A/V info including SET_GEOMETRY / SET_SYSTEM_AV_INFO updates:
// [baseWidth, baseHeight, fps*1_000_000, sampleRate, maxWidth, maxHeight], or null before load.
JNIEXPORT jintArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetCurrentAvInfo(JNIEnv *env, jobject) {
    if (!g_geometry_valid) return nullptr;
    jint vals[6] = {
        (jint)g_geometry.base_width,
        (jint)g_geometry.base_height,
        (jint)(g_timing.fps * 1000000),
        (jint)g_timing.sample_rate,
        (jint)g_geometry.max_width,
        (jint)g_geometry.max_height
    };
    jintArray result = env->NewIntArray(6);
    env->SetIntArrayRegion(result, 0, 6, vals);
    return result;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetInput(JNIEnv *, jobject, jint port, jint mask) {
    if (port >= 0 && port < MAX_PORTS)
        g_input_state[port] = (int16_t)mask;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetPointer(JNIEnv *, jobject, jint x, jint y, jboolean pressed) {
    g_pointer_x = (int16_t)x;
    g_pointer_y = (int16_t)y;
    g_pointer_pressed = pressed ? 1 : 0;
}

JNIEXPORT jobjectArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetControllerTypes(JNIEnv *env, jobject, jint port) {
    jclass strClass = env->FindClass("java/lang/String");
    if (port < 0 || port >= MAX_PORTS || g_controller_types[port].empty()) {
        return env->NewObjectArray(0, strClass, nullptr);
    }
    auto &types = g_controller_types[port];
    int count = (int)types.size();
    jobjectArray result = env->NewObjectArray(count * 2, strClass, nullptr);
    for (int i = 0; i < count; i++) {
        env->SetObjectArrayElement(result, i * 2, env->NewStringUTF(types[i].desc.c_str()));
        char idStr[16];
        snprintf(idStr, sizeof(idStr), "%u", types[i].id);
        env->SetObjectArrayElement(result, i * 2 + 1, env->NewStringUTF(idStr));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetAnalog(JNIEnv *, jobject, jint port, jint index, jint x, jint y) {
    if (port >= 0 && port < MAX_PORTS && index >= 0 && index < 2) {
        g_analog_state[port][index][0] = (int16_t)x;
        g_analog_state[port][index][1] = (int16_t)y;
    }
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetControllerPortDevice(JNIEnv *, jobject, jint port, jint device) {
    if (core.set_controller_port_device && port >= 0 && port < MAX_PORTS)
        core.set_controller_port_device((unsigned)port, (unsigned)device);
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetRotation(JNIEnv *, jobject) {
    return (jint)g_rotation;
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetPixelFormat(JNIEnv *, jobject) {
    // HW frames are read back as RGBA bytes, i.e. the converted XRGB8888 layout
    if (g_hw_requested) return (jint)RETRO_PIXEL_FORMAT_XRGB8888;
    // 0RGB1555 is converted to RGB565 in video_refresh_cb
    unsigned effective = (g_pixel_format == RETRO_PIXEL_FORMAT_0RGB1555)
        ? RETRO_PIXEL_FORMAT_RGB565 : g_pixel_format;
    return (jint)effective;
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetFrameWidth(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    return (jint)g_frame_width;
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetFrameHeight(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    return (jint)g_frame_height;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeHasNewFrame(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    return g_frame_ready ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeCopyFrame(JNIEnv *env, jobject, jobject buffer) {
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    if (g_hw_requested) {
        // GL thread only: reads the FBO back (skipped without a current context)
        if (!g_frame_ready) return;
        hw_read_frame((uint8_t *)env->GetDirectBufferAddress(buffer),
                      (size_t)env->GetDirectBufferCapacity(buffer), g_frame_width, g_frame_height);
        g_frame_ready = false;
        return;
    }
    if (!g_frame_buf || !g_frame_ready) return;
    void *dst = env->GetDirectBufferAddress(buffer);
    if (!dst) return;
    size_t bpp = (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) ? 4 : 2;
    size_t size = g_frame_width * g_frame_height * bpp;
    memcpy(dst, g_frame_buf, size);
    g_frame_ready = false;
}

// Returns false if nothing was copied (no frame yet, or for HW cores no
// current GL context -- the buffer is then left untouched).
JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeCopyLastFrame(JNIEnv *env, jobject, jobject buffer) {
    std::lock_guard<std::mutex> lock(g_frame_mutex);
    if (g_hw_requested) {
        // GL thread only: reads the FBO back (skipped without a current context)
        bool ok = hw_read_frame((uint8_t *)env->GetDirectBufferAddress(buffer),
                                (size_t)env->GetDirectBufferCapacity(buffer), g_frame_width, g_frame_height);
        return ok ? JNI_TRUE : JNI_FALSE;
    }
    if (!g_frame_buf) return JNI_FALSE;
    void *dst = env->GetDirectBufferAddress(buffer);
    if (!dst) return JNI_FALSE;
    size_t bpp = (g_pixel_format == RETRO_PIXEL_FORMAT_XRGB8888) ? 4 : 2;
    size_t size = g_frame_width * g_frame_height * bpp;
    memcpy(dst, g_frame_buf, size);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSaveState(JNIEnv *env, jobject, jstring path) {
    size_t size = core.serialize_size();
    if (size == 0) return JNI_FALSE;

    void *buf = malloc(size);
    bool serialized = core.serialize(buf, size);
    if (hw_context_current()) hw_restore_gl_state();
    if (!serialized) {
        free(buf);
        return JNI_FALSE;
    }

    const char *p = env->GetStringUTFChars(path, nullptr);
    FILE *f = fopen(p, "wb");
    env->ReleaseStringUTFChars(path, p);
    if (!f) { free(buf); return JNI_FALSE; }

    fwrite(buf, 1, size, f);
    fclose(f);
    free(buf);
    return JNI_TRUE;
}

static void *rzip_decompress(const void *src, size_t src_size, size_t *out_size) {
    const uint8_t *data = (const uint8_t *)src;
    if (src_size < 16 || memcmp(data, "#RZIPv", 6) != 0) return nullptr;

    uint64_t uncompressed_size;
    memcpy(&uncompressed_size, data + 8, 8);

    uint32_t chunk_size = (data[6] == '2') ? 1048576 : 131072;

    void *out = malloc((size_t)uncompressed_size);
    if (!out) return nullptr;

    size_t src_off = 16, dst_off = 0;
    while (src_off + 4 <= src_size && dst_off < uncompressed_size) {
        uint32_t comp_size;
        memcpy(&comp_size, data + src_off, 4);
        src_off += 4;

        if (comp_size == 0) {
            size_t raw = (uncompressed_size - dst_off < chunk_size)
                ? (size_t)(uncompressed_size - dst_off) : chunk_size;
            if (src_off + raw > src_size) { free(out); return nullptr; }
            memcpy((uint8_t *)out + dst_off, data + src_off, raw);
            dst_off += raw;
            src_off += raw;
        } else {
            if (src_off + comp_size > src_size) { free(out); return nullptr; }
            uLongf dest_len = (uLongf)(uncompressed_size - dst_off);
            if (uncompress((Bytef *)out + dst_off, &dest_len,
                           data + src_off, comp_size) != Z_OK) {
                free(out); return nullptr;
            }
            dst_off += dest_len;
            src_off += comp_size;
        }
    }

    *out_size = (size_t)dst_off;
    return out;
}

static bool rastate_extract_mem(const void *src, size_t src_size, const void **out, size_t *out_size) {
    const uint8_t *data = (const uint8_t *)src;
    if (src_size < 8 || memcmp(data, "RASTATE", 7) != 0) return false;

    size_t off = 8;
    while (off + 8 <= src_size) {
        uint32_t block_size;
        memcpy(&block_size, data + off + 4, 4);
        if (memcmp(data + off, "MEM ", 4) == 0) {
            if (off + 8 + block_size > src_size) return false;
            *out = data + off + 8;
            *out_size = block_size;
            return true;
        }
        off += 8 + block_size;
    }
    return false;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeLoadState(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    FILE *f = fopen(p, "rb");
    env->ReleaseStringUTFChars(path, p);
    if (!f) return JNI_FALSE;

    fseek(f, 0, SEEK_END);
    long file_size = ftell(f);
    fseek(f, 0, SEEK_SET);
    void *file_buf = malloc(file_size);
    size_t read = fread(file_buf, 1, file_size, f);
    fclose(f);
    if (read != (size_t)file_size) {
        LOGE("nativeLoadState: short read (%zu of %zu)", read, (size_t)file_size);
        free(file_buf);
        return JNI_FALSE;
    }

    const void *state_data = nullptr;
    size_t state_size = 0;
    void *alloc_buf = nullptr;
    bool ok = false;

    if (rastate_extract_mem(file_buf, file_size, &state_data, &state_size)) {
        ok = core.unserialize(const_cast<void*>(state_data), state_size);
    } else {
        alloc_buf = rzip_decompress(file_buf, file_size, &state_size);
        if (alloc_buf) {
            ok = core.unserialize(alloc_buf, state_size);
        } else {
            ok = core.unserialize(file_buf, file_size);
        }
    }

    free(alloc_buf);
    free(file_buf);
    if (hw_context_current()) hw_restore_gl_state();
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSaveSRAM(JNIEnv *env, jobject, jstring path) {
    void *data = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return JNI_FALSE;

    const char *p = env->GetStringUTFChars(path, nullptr);
    FILE *f = fopen(p, "wb");
    env->ReleaseStringUTFChars(path, p);
    if (!f) return JNI_FALSE;

    fwrite(data, 1, size, f);
    fclose(f);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeLoadSRAM(JNIEnv *env, jobject, jstring path) {
    void *data = core.get_memory_data(RETRO_MEMORY_SAVE_RAM);
    size_t size = core.get_memory_size(RETRO_MEMORY_SAVE_RAM);
    if (!data || size == 0) return JNI_FALSE;

    const char *p = env->GetStringUTFChars(path, nullptr);
    FILE *f = fopen(p, "rb");
    env->ReleaseStringUTFChars(path, p);
    if (!f) return JNI_FALSE;

    fread(data, 1, size, f);
    fclose(f);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeApplyEmuCheats(JNIEnv *env, jobject, jobjectArray codes) {
    if (!core.cheat_reset || !core.cheat_set) return;
    core.cheat_reset();
    jsize n = env->GetArrayLength(codes);
    unsigned idx = 0;
    for (jsize i = 0; i < n; i++) {
        jstring jcode = (jstring)env->GetObjectArrayElement(codes, i);
        if (!jcode) continue;
        const char *code = env->GetStringUTFChars(jcode, nullptr);
        if (code && code[0]) core.cheat_set(idx++, true, code);
        if (code) env->ReleaseStringUTFChars(jcode, code);
        env->DeleteLocalRef(jcode);
    }
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetRetroCheats(JNIEnv *env, jobject, jlongArray table) {
    if (!table) {
        cheat_engine_clear();
        return;
    }
    jsize len = env->GetArrayLength(table);
    if (len <= 0) {
        cheat_engine_clear();
        return;
    }
    std::vector<int64_t> buf((size_t)len);
    env->GetLongArrayRegion(table, 0, len, (jlong *)buf.data());
    cheat_engine_set_table(buf.data(), (size_t)(len / CHEAT_ENGINE_STRIDE));
}

JNIEXPORT jlong JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeCheatMemorySize(JNIEnv *, jobject) {
    return (jlong)cheat_engine_total_memory();
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeUnloadGame(JNIEnv *, jobject) {
    // Frontend order for HW cores: context_destroy, then retro_unload_game.
    hw_context_destroy();
    core.unload_game();
    if (eglGetCurrentContext() != EGL_NO_CONTEXT && g_hw_requested) hw_restore_gl_state();
    g_pointer_x = 0;
    g_pointer_y = 0;
    g_pointer_pressed = 0;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeDeinit(JNIEnv *env, jobject) {
    hw_context_destroy();  // no-op if unloadGame already destroyed it
    core.deinit();
    hw_reset_state();
    g_geometry_valid = false;
    if (core.handle) {
        dlclose(core.handle);
        core.handle = nullptr;
    }
    nativeAudioStop();
    {
        std::lock_guard<std::mutex> lock(g_frame_mutex);
        free(g_frame_buf);
        g_frame_buf = nullptr;
        g_frame_width = 0;
        g_frame_height = 0;
        g_frame_ready = false;
    }
    memset(&g_disk_control, 0, sizeof(g_disk_control));
    g_has_disk_control = false;
    g_get_image_label = nullptr;
    for (int p = 0; p < MAX_PORTS; p++) g_controller_types[p].clear();
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeReset(JNIEnv *, jobject) {
    if (core.reset) core.reset();
    if (hw_context_current()) hw_restore_gl_state();
}

JNIEXPORT jobjectArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetCoreOptions(JNIEnv *env, jobject) {
    jclass strClass = env->FindClass("java/lang/String");
    int count = (int)g_core_options.size();
    jobjectArray result = env->NewObjectArray(count * 7, strClass, nullptr);
    for (int i = 0; i < count; i++) {
        auto &opt = g_core_options[i];
        std::string vals;
        std::string labels;
        for (size_t j = 0; j < opt.values.size(); j++) {
            if (j > 0) { vals += '|'; labels += '|'; }
            vals += opt.values[j].value;
            labels += opt.values[j].label;
        }
        auto it = g_option_overrides.find(opt.key);
        const std::string &sel = (it != g_option_overrides.end()) ? it->second : opt.selected;
        env->SetObjectArrayElement(result, i * 7 + 0, env->NewStringUTF(opt.key.c_str()));
        env->SetObjectArrayElement(result, i * 7 + 1, env->NewStringUTF(opt.desc.c_str()));
        env->SetObjectArrayElement(result, i * 7 + 2, env->NewStringUTF(vals.c_str()));
        env->SetObjectArrayElement(result, i * 7 + 3, env->NewStringUTF(sel.c_str()));
        env->SetObjectArrayElement(result, i * 7 + 4, env->NewStringUTF(opt.category.c_str()));
        env->SetObjectArrayElement(result, i * 7 + 5, env->NewStringUTF(labels.c_str()));
        env->SetObjectArrayElement(result, i * 7 + 6, env->NewStringUTF(opt.info.c_str()));
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetCoreCategories(JNIEnv *env, jobject) {
    jclass strClass = env->FindClass("java/lang/String");
    int count = (int)g_core_categories.size();
    jobjectArray result = env->NewObjectArray(count * 3, strClass, nullptr);
    for (int i = 0; i < count; i++) {
        env->SetObjectArrayElement(result, i * 3 + 0, env->NewStringUTF(g_core_categories[i].key.c_str()));
        env->SetObjectArrayElement(result, i * 3 + 1, env->NewStringUTF(g_core_categories[i].desc.c_str()));
        env->SetObjectArrayElement(result, i * 3 + 2, env->NewStringUTF(g_core_categories[i].info.c_str()));
    }
    return result;
}

JNIEXPORT void JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetCoreOption(JNIEnv *env, jobject, jstring key, jstring value) {
    const char *k = env->GetStringUTFChars(key, nullptr);
    const char *v = env->GetStringUTFChars(value, nullptr);
    std::string k_str(k);
    std::string v_str(v);
    g_option_overrides[k_str] = v_str;
    g_options_dirty = true;
    env->ReleaseStringUTFChars(key, k);
    env->ReleaseStringUTFChars(value, v);

    if (!core.set_controller_port_device) return;

    std::string key_lower = k_str;
    for (auto &c : key_lower) c = tolower(c);
    if (key_lower.find("type") == std::string::npos && key_lower.find("device") == std::string::npos) return;

    int port = -1;
    size_t pos = std::string::npos;
    if ((pos = key_lower.find("pad")) != std::string::npos) pos += 3;
    else if ((pos = key_lower.find("player")) != std::string::npos) pos += 6;
    else if ((pos = key_lower.find("_p")) != std::string::npos) pos += 2;
    else if ((pos = key_lower.find("controller")) != std::string::npos) pos += 10;

    if (pos != std::string::npos && pos < key_lower.size() && key_lower[pos] >= '1' && key_lower[pos] <= '4')
        port = key_lower[pos] - '1';

    if (port < 0 || port >= MAX_PORTS) return;

    std::string val_lower = v_str;
    for (auto &c : val_lower) c = tolower(c);
    for (auto &ct : g_controller_types[port]) {
        std::string desc_lower = ct.desc;
        for (auto &c : desc_lower) c = tolower(c);
        if (desc_lower == val_lower || desc_lower.find(val_lower) != std::string::npos ||
            val_lower.find(desc_lower) != std::string::npos) {
            core.set_controller_port_device(port, ct.id);
            break;
        }
    }
}

JNIEXPORT jobjectArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetSystemInfo(JNIEnv *env, jobject) {
    struct retro_system_info info = {0};
    core.get_system_info(&info);

    jobjectArray result = env->NewObjectArray(2, env->FindClass("java/lang/String"), nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF(info.library_name ? info.library_name : ""));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF(info.library_version ? info.library_version : ""));
    return result;
}

JNIEXPORT jfloat JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetAspectRatio(JNIEnv *, jobject) {
    // HW cores: use the geometry tracked from env calls rather than calling
    // into the core off its thread (PPSSPP's get_system_av_info has side effects).
    if (g_hw_requested && g_geometry_valid) {
        float hw_ar = g_geometry.aspect_ratio;
        if (hw_ar <= 0.0f && g_geometry.base_height > 0)
            hw_ar = (float)g_geometry.base_width / (float)g_geometry.base_height;
        if (hw_ar > 0.0f) return hw_ar;
    }
    struct retro_system_av_info av_info;
    core.get_system_av_info(&av_info);
    float ar = av_info.geometry.aspect_ratio;
    if (ar <= 0.0f) {
        ar = (float)av_info.geometry.base_width / (float)av_info.geometry.base_height;
    }
    return ar;
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeHasDiskControl(JNIEnv *, jobject) {
    return g_has_disk_control ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetDiskCount(JNIEnv *, jobject) {
    if (!g_has_disk_control || !g_disk_control.get_num_images) return 0;
    return (jint)g_disk_control.get_num_images();
}

JNIEXPORT jint JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetDiskIndex(JNIEnv *, jobject) {
    if (!g_has_disk_control || !g_disk_control.get_image_index) return 0;
    return (jint)g_disk_control.get_image_index();
}

JNIEXPORT jboolean JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeSetDiskIndex(JNIEnv *, jobject, jint index) {
    if (!g_has_disk_control || !g_disk_control.set_eject_state || !g_disk_control.set_image_index) return JNI_FALSE;
    g_disk_control.set_eject_state(true);
    bool ok = g_disk_control.set_image_index((unsigned)index);
    g_disk_control.set_eject_state(false);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetDiskLabel(JNIEnv *env, jobject, jint index) {
    if (g_get_image_label) {
        const char *label = g_get_image_label((unsigned)index);
        if (label && label[0]) return env->NewStringUTF(label);
    }
    return nullptr;
}

} // extern "C"

static FrameBuffer g_shared_frame;

extern "C" FrameBuffer *getFrameBuffer() {
    g_frame_mutex.lock();
    g_shared_frame.data = g_frame_buf;
    g_shared_frame.width = g_frame_width;
    g_shared_frame.height = g_frame_height;
    g_shared_frame.pitch = g_frame_pitch;
    g_shared_frame.pixel_format = g_pixel_format;
    g_shared_frame.ready = g_frame_ready;
    return &g_shared_frame;
}

extern "C" void markFrameConsumed() {
    g_frame_ready = false;
    g_frame_mutex.unlock();
}

extern "C" const struct retro_memory_map *bridge_get_memory_map(void) {
    return g_memory_descriptor_count > 0 ? &g_memory_map : nullptr;
}

extern "C" void *bridge_get_memory_data(unsigned id) {
    return core.get_memory_data ? core.get_memory_data(id) : nullptr;
}

extern "C" size_t bridge_get_memory_size(unsigned id) {
    return core.get_memory_size ? core.get_memory_size(id) : 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetMemoryDescriptors(JNIEnv *env, jobject) {
    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray((jsize)g_memory_descriptor_count, strClass, nullptr);
    for (unsigned i = 0; i < g_memory_descriptor_count; i++) {
        const auto &d = g_memory_descriptors[i];
        char buf[256];
        snprintf(buf, sizeof(buf),
            "[%u] flags=0x%llx ptr=%s offset=0x%zx start=0x%zx select=0x%zx disconnect=0x%zx len=0x%zx addrspace=%s",
            i,
            (unsigned long long)d.flags,
            d.ptr ? "set" : "null",
            d.offset, d.start, d.select, d.disconnect, d.len,
            d.addrspace ? d.addrspace : "");
        env->SetObjectArrayElement(result, (jsize)i, env->NewStringUTF(buf));
    }
    return result;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_cannelloni_scorza_libretro_LibretroRunner_nativeGetCoreLogs(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_log_ring_mutex);
    jclass strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(g_log_ring_count, strClass, nullptr);
    int start = (g_log_ring_head - g_log_ring_count + LOG_RING_SIZE) % LOG_RING_SIZE;
    for (int i = 0; i < g_log_ring_count; i++) {
        int idx = (start + i) % LOG_RING_SIZE;
        env->SetObjectArrayElement(result, i, env->NewStringUTF(g_log_ring[idx].c_str()));
    }
    g_log_ring_count = 0;
    g_log_ring_head = 0;
    return result;
}

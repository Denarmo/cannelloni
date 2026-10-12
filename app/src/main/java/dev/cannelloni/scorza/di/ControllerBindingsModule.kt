package dev.cannelloni.scorza.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.cannelloni.scorza.config.CannelloniPaths
import dev.cannelloni.scorza.input.autoconfig.AssetCfgSource
import dev.cannelloni.scorza.input.autoconfig.AutoconfigLoader
import dev.cannelloni.scorza.input.autoconfig.BundledAutoconfigEntries
import dev.cannelloni.scorza.input.repo.MappingRepository
import dev.cannelloni.scorza.input.resolver.MappingResolver
import dev.cannelloni.scorza.input.runtime.ActiveMappingHolder
import dev.cannelloni.scorza.input.runtime.ControllerBridge
import dev.cannelloni.scorza.input.runtime.PortRouter
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BundledRetroArchAutoconfig

@Module
@InstallIn(SingletonComponent::class)
object ControllerBindingsModule {

    @Provides
    @Singleton
    fun provideMappingRepository(paths: CannelloniPathsProvider): MappingRepository =
        MappingRepository { CannelloniPaths(paths.root).configInputMappings }

    @Provides
    @Singleton
    fun provideBundledAutoconfigEntries(
        @ApplicationContext context: Context,
    ): BundledAutoconfigEntries =
        BundledAutoconfigEntries(AutoconfigLoader(AssetCfgSource(context)).entries())

    @Provides
    @Singleton
    fun provideMappingResolver(
        repository: MappingRepository,
        bundled: BundledAutoconfigEntries,
        paths: CannelloniPathsProvider,
        hints: dev.cannelloni.scorza.input.hints.ControllerHintTable,
    ): MappingResolver = MappingResolver(
        repository = repository,
        bundledRetroArchEntries = bundled,
        hints = hints,
        mappingsDir = CannelloniPaths(paths.root).configInputMappings,
    )

    @Provides
    @Singleton
    fun provideControllerHintTable(
        @ApplicationContext context: Context,
    ): dev.cannelloni.scorza.input.hints.ControllerHintTable =
        dev.cannelloni.scorza.input.hints.ControllerHintTable.fromAssets(context)

    @Provides
    @Singleton
    fun providePortRouter(): PortRouter = PortRouter()

    @Provides
    @Singleton
    fun provideActiveMappingHolder(): ActiveMappingHolder = ActiveMappingHolder()

    @Provides
    @Singleton
    fun provideControllerBridge(
        resolver: MappingResolver,
        portRouter: PortRouter,
        activeMappingHolder: ActiveMappingHolder,
        mappingRepository: MappingRepository,
        blacklist: dev.cannelloni.scorza.input.ControllerBlacklist,
        bundled: BundledAutoconfigEntries,
        hints: dev.cannelloni.scorza.input.hints.ControllerHintTable,
    ): ControllerBridge = ControllerBridge(
        resolver = resolver,
        portRouter = portRouter,
        activeMappingHolder = activeMappingHolder,
        mappingRepository = mappingRepository,
        blacklist = blacklist,
        bundledCfgs = bundled,
        hints = hints,
        devKeyboardEnabled = dev.cannelloni.scorza.BuildConfig.DEBUG &&
            dev.cannelloni.scorza.util.DeviceType.isAvd(),
    )
}

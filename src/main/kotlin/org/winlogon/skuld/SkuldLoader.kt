package org.winlogon.skuld

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class SkuldLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()

        resolver.addRepository(
            RemoteRepository.Builder(
                "central", 
                "default", 
                "https://repo.maven.apache.org/maven2/"
            ).build()
        )

        val dependencies = mapOf(
            "org.postgresql:postgresql" to "42.7.7",
            "org.json:json" to "20250107",
            "com.github.ben-manes.caffeine:caffeine" to "3.2.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core" to "1.10.2"
        )

        dependencies.forEach {
            val artifact = DefaultArtifact("${it.key}:${it.value}")
            println("Adding ${it.key}:${it.value}")
            resolver.addDependency(Dependency(artifact, null))
        }

        classpathBuilder.addLibrary(resolver)
    }
}

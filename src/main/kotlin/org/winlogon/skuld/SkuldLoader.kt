// SPDX-License-Identifier: MPL-2.0
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

        val repositories = mapOf(
            "central" to MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR,
            "winlogon-code" to "https://maven.winlogon.org/releases",
            "jitpack" to "https://jitpack.io",
        )

        repositories.forEach { (name, url) ->
            resolver.addRepository(
                RemoteRepository.Builder(name, "default", url).build()
            )
        }

        val dependencies = mapOf(
            "org.postgresql:postgresql" to "42.7.7",
            // TODO: the sqlite-jdbc library fails to download with MAVEN_CENTRAL_DEFAULT_MIRROR. Right now I'm keeping it
            // runtimeOnly, but it should be later changed to compileOnly and uncommend the line below.
            // "org.xerial:sqlite-jdbc" to "3.50.3.0",
            "com.mysql:mysql-connector-j" to "9.3.0",

            "org.json:json" to "20250107",
            "com.github.ben-manes.caffeine:caffeine" to "3.2.0",
            "org.winlogon:xpconomy" to "0.2.3",

            "de.exlll:configlib-core" to "4.6.4",
            "de.exlll:configlib-yaml" to "4.6.4",
            "de.exlll:configlib-paper" to "4.6.4",

            "org.jetbrains.exposed:exposed-core" to "1.3.0",
            "org.jetbrains.exposed:exposed-jdbc" to "1.3.0",
            "com.zaxxer:HikariCP" to "6.2.1",
        )

        dependencies.forEach {
            val artifact = DefaultArtifact("${it.key}:${it.value}")
            resolver.addDependency(Dependency(artifact, null))
        }

        classpathBuilder.addLibrary(resolver)
    }
}

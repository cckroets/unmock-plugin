/*
   Copyright (C) 2015 Björn Quentin

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package de.mobilej.unmock

import com.android.build.gradle.api.BaseVariant
import de.mobilej.UnMockTask
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class UnMockPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.configurations.create("unmock")

        def unMockExt = project.extensions.create("unMock", UnMockExtension)

        //create a unique configuration with a default dependency to android jar
        project.configurations["unmock"].defaultDependencies { dependencies ->
            // If the user doesn't add any dependencies to the unmock configuration, this will be used
            dependencies.add(project.dependencies.create("org.robolectric:android-all:4.3_r2-robolectric-0"))
        }

        def outputJarPath = "${project.buildDir}/intermediates/unmocked-android${project.name}.jar"

        //create a unique task to unmock for all variants, the task uses the unique configuration
        def unMockTask = project.tasks.register("unMock", UnMockTask.class) {
            if (project.unMock.allAndroid != null) {
                throw new GradleException("Using 'downloadFrom' is unsupported now. Please use the unmock scope to define the android-all.jar. See https://github.com/bjoernQ/unmock-plugin/blob/master/README.md")
            }

            allAndroid = project.configurations["unmock"]
            outputDir = project.file("${project.buildDir}/intermediates/unmock_work")
            unmockedOutputJar = project.file(outputJarPath)
            keepClasses = unMockExt.keep
            renameClasses = unMockExt.rename
            delegateClasses = unMockExt.delegateClasses
        }

        //this dependency is provided by the unique task: when gradle will need it, it will run the task,
        // prior to compilation
        def outputJarDependency = project.files(outputJarPath).builtBy(unMockTask)

        def isLib = project.plugins.findPlugin('com.android.library')
        def isApp = project.plugins.findPlugin('com.android.application')

        // Use custom variants if specified, otherwise fallback to just unit tests for apps and libs
        project.afterEvaluate {
            def mainVariants = unMockExt.variants ?: (isLib || isApp ? project.android.unitTestVariants : null)
            mainVariants?.all { variant ->
                variant.registerPreJavacGeneratedBytecode(outputJarDependency)
            }
        }
    }
}

class UnMockExtension {

    String allAndroid

    String downloadTo

    List<String> keep = new ArrayList<>()

    List<String> rename = new ArrayList<>()

    List<String> delegateClasses = new ArrayList<>()

    boolean usingDefaults = false

    DomainObjectSet<BaseVariant> variants

    public UnMockExtension() {
        keep "android.widget.BaseAdapter"
        keep "android.widget.ArrayAdapter"
        keep "android.os.Bundle"
        keepStartingWith "android.database.MatrixCursor"
        keep "android.database.AbstractCursor"
        keep "android.database.CrossProcessCursor"
        keepStartingWith "android.text.TextUtils"
        keepStartingWith "android.util."
        keepStartingWith "android.text."
        keepStartingWith "android.content.ContentValues"
        keepStartingWith "android.content.ComponentName"
        keepStartingWith "android.content.ContentUris"
        keepStartingWith "android.content.ContentProviderOperation"
        keepStartingWith "android.content.ContentProviderResult"
        keepStartingWith "android.content.UriMatcher"
        keepStartingWith "android.content.Intent"
        keep "android.location.Location"
        keepStartingWith "android.content.res.Configuration"
        keepStartingWith "org."
        keepStartingWith "libcore."
        keepStartingWith "com.android.internal.R"
        keepStartingWith "com.android.internal.util."
        keep "android.net.Uri"

        keepAndRename "java.nio.charset.Charsets" to "xjava.nio.charset.Charsets"

        usingDefaults = true
    }

    DownloadTo downloadFrom(final String allAndroidUrl) {
        allAndroid = allAndroidUrl
        return new DownloadTo(this)
    }

    void keep(final String clazz) {
        clearDefaultIfNecessary()
        keep.add("-" + clazz)
    }

    void delegateClass(final String clazz) {
        clearDefaultIfNecessary()
        delegateClasses.add(clazz)
    }

    void keepStartingWith(final String clazz) {
        clearDefaultIfNecessary()
        keep.add(clazz)
    }

    void includeInVariants(final DomainObjectSet<BaseVariant> customVariants) {
        this.variants = customVariants
    }

    KeepMapping keepAndRename(final String clazzToKeep) {
        clearDefaultIfNecessary()
        return new KeepMapping(clazzToKeep, this)
    }

    private void clearDefaultIfNecessary() {
        if (usingDefaults) {
            usingDefaults = false
            keep.clear()
            rename.clear()
        }
    }
}

class KeepMapping {
    String keep
    UnMockExtension extension

    KeepMapping(final String whatToKeep, UnMockExtension extension) {
        keep = whatToKeep
        this.extension = extension
    }

    void to(final String renameTo) {
        extension.rename.add(keep + "=" + renameTo)
    }
}

class DownloadTo {
    String to
    UnMockExtension extension

    DownloadTo(UnMockExtension extension) {
        this.extension = extension
    }

    void to(final String where) {
        extension.downloadTo = where
    }
}

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import java.util.zip.ZipOutputStream

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)

    configurations.configureEach {
        resolutionStrategy.force(
            "androidx.annotation:annotation-experimental:1.4.0",
            "androidx.core:core:1.13.1",
            "androidx.core:core-ktx:1.13.1",
            "androidx.lifecycle:lifecycle-common:2.7.0",
            "androidx.lifecycle:lifecycle-common-java8:2.7.0",
            "androidx.lifecycle:lifecycle-livedata:2.7.0",
            "androidx.lifecycle:lifecycle-livedata-core:2.7.0",
            "androidx.lifecycle:lifecycle-livedata-core-ktx:2.7.0",
            "androidx.lifecycle:lifecycle-process:2.7.0",
            "androidx.lifecycle:lifecycle-runtime:2.7.0",
            "androidx.lifecycle:lifecycle-viewmodel:2.7.0",
            "androidx.lifecycle:lifecycle-viewmodel-savedstate:2.7.0",
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.24",
            "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24",
        )
    }

    pluginManager.withPlugin("com.android.library") {
        extensions.configure<LibraryAndroidComponentsExtension> {
            finalizeDsl { libraryExtension ->
                libraryExtension.compileSdk = 36
                libraryExtension.buildToolsVersion = "36.0.0"
                libraryExtension.ndkVersion = "27.0.12077973"
            }
        }

        // These Flutter plugins are embedded in the APK and are not published as
        // standalone AARs. Skip AGP's publication-only annotation extraction so
        // offline release builds do not need the full lint-gradle toolchain.
        // Downstream AAR tasks still expect its two files, so create valid empty
        // placeholders in a separate task without touching task inputs at all.
        val prepareEmptyReleaseAnnotations =
            tasks.register("prepareEmptyReleaseAnnotations") {
                doLast {
                    val annotationsZip =
                        layout.buildDirectory
                            .file(
                                "intermediates/annotations_zip/release/" +
                                    "extractReleaseAnnotations/annotations.zip",
                            )
                            .get()
                            .asFile
                    val typedefFile =
                        layout.buildDirectory
                            .file(
                                "intermediates/annotations_typedef_file/release/" +
                                    "extractReleaseAnnotations/typedefs.txt",
                            )
                            .get()
                            .asFile

                    annotationsZip.parentFile.mkdirs()
                    annotationsZip.outputStream().use { stream ->
                        ZipOutputStream(stream).use { }
                    }
                    typedefFile.parentFile.mkdirs()
                    typedefFile.writeText("")
                }
            }

        tasks.configureEach {
            if (name == "extractReleaseAnnotations") {
                enabled = false
            }
            // The application remains covered by its own release checks. Avoid
            // resolving a separate lint engine for each embedded plugin AAR.
            if (name.startsWith("lintVital")) {
                enabled = false
            }
            if (name == "syncReleaseLibJars" || name == "bundleReleaseAar") {
                dependsOn(prepareEmptyReleaseAnnotations)
            }
        }
    }
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

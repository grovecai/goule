pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("""com\.android(\..*)?""")
                includeGroupByRegex("""androidx\..*""")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("""com\.android(\..*)?""")
                includeGroupByRegex("""androidx\..*""")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Goule"
include(":app")

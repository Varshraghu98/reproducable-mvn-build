import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.triggers.vcs

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2025.07"

project {

    buildType(Build)
}

object Build : BuildType({
    name = "Build"

    params {
        param("commitHash", "5d12199671ed549f6bd018c52017ac83526afa1f")
        text("env.GIT_COMMIT", "", display = ParameterDisplay.PROMPT, allowEmpty = false,
                regex = """[a-f0-9]{7,40}""", regexFailureMessage = "Enter a valid git SHA")
    }

    // Checkout happens automatically BEFORE steps, set it to On Agent
    checkoutMode = CheckoutMode.ON_AGENT


    vcs {
        root(HttpsGithubComVarshraghu98reproducableMvnBuildRefsHeadsMain)
        cleanCheckout = true
    }

    steps {
        script {
            name = "Checkout specific commit"
            scriptContent = """
                set -euo pipefail
                echo ">> Fetching and checking out %env.GIT_COMMIT%"
                git fetch --all --tags --prune
                git checkout --force %env.GIT_COMMIT%
                if [ -f .gitmodules ]; then
                  git submodule sync --recursive
                  git submodule update --init --recursive --checkout
                fi
                echo ">> Now at: $(git rev-parse HEAD)"
            """.trimIndent()
        }
        maven {
            name = "Create maven package"
            id = "Create_maven_package"
            goals = "-DskipTests clean package"
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
    }
})

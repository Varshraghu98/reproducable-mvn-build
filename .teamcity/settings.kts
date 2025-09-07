import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

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

    vcsRoot(HttpsGithubComVarshraghu98reproducableMvnBuildGitRefsHeadsMain)
    vcsRoot(RepoVcs)

    buildType(Build)
}

object Build : BuildType({
    name = "Build"

    artifactRules = "target/repro-docs-*.zip"

    params {
        param("env.GIT_COMMIT", "")
    }

    vcs {
        root(RepoVcs)

        checkoutMode = CheckoutMode.ON_AGENT
        cleanCheckout = true
    }

    steps {
        script {
            name = "Checkout specific commit"
            scriptContent = """
                set -eu
                echo ">> Fetching and checking out %env.GIT_COMMIT%"
                # Fetch just the required commit (and tags) into a local ref with the same SHA
                git fetch --tags --prune origin +%env.GIT_COMMIT%:%env.GIT_COMMIT%
                git checkout --force %env.GIT_COMMIT%
                echo ">> Now at: ${'$'}(git rev-parse HEAD)"
            """.trimIndent()
        }
        maven {
            name = "Create maven package"
            goals = "-Dmaven.test.skip=true clean package"
        }
        script {
            name = "SHA256 checksums"
            scriptContent = """
                chmod +x buildscripts/print-sha256.sh
                CHECKOUT_DIR="%teamcity.build.checkoutDir%" DOCS_PATTERN="repro-docs-*.zip" ./buildscripts/print-sha256.sh
            """.trimIndent()
        }
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
        sshAgent {
            teamcitySshKey = "tc-release-bot"
        }
    }
})

object HttpsGithubComVarshraghu98reproducableMvnBuildGitRefsHeadsMain : GitVcsRoot({
    name = "https://github.com/Varshraghu98/reproducable-mvn-build.git#refs/heads/main"
    url = "https://github.com/Varshraghu98/reproducable-mvn-build.git"
    branch = "refs/heads/main"
    branchSpec = "refs/heads/*"
    authMethod = password {
        userName = "Varshraghu98"
        password = "credentialsJSON:10689a50-54a2-407c-8356-90807ec8374b"
    }
})

object RepoVcs : GitVcsRoot({
    name = "reproducable-mvn-build (SSH)"
    url = "git@github.com:Varshraghu98/reproducable-mvn-build.git"
    branch = "refs/heads/main"
    branchSpec = """
        +:refs/heads/*
        +:refs/tags/*
    """.trimIndent()
    authMethod = uploadedKey {
        uploadedKey = "tc-release-bot"
    }
    param("teamcity.git.useNativeSsh", "true")
    param("useAlternates", "true")
    param("teamcity.git.clone.depth", "0")
})

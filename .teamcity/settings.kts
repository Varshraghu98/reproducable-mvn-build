import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

version = "2025.07"

project {
    // register VCS root and build type
    vcsRoot(RepoVcs)
    buildType(Build)
}

object RepoVcs : GitVcsRoot({
    id("RepoVcs")
    name = "reproducable-mvn-build"
    url = "https://github.com/Varshraghu98/reproducable-mvn-build.git"
    branch = "refs/heads/main"
    // do checkout on the agent (we need a real .git)
    checkoutPolicy = GitVcsRoot.CheckoutPolicy.ON_AGENT
    // deterministic submodules at pinned SHAs
    param("teamcity.git.submoduleCheckout", "CHECKOUT")
    // full history so any SHA can be fetched
    param("teamcity.git.clone.depth", "0")
})

object Build : BuildType({
    name = "Build"

    params {
        // Prompt for the commit SHA when you click "Run…"
        text(
                "env.GIT_COMMIT",
                "",
                display = ParameterDisplay.PROMPT,
                allowEmpty = false
        ) {
            // optional validation (works in recent DSL)
            // remove this block if your server complains
            regex = "[a-f0-9]{7,40}"
            regexMessage = "Enter a valid git SHA (7–40 hex chars)"
        }
    }

    // checkout happens before steps
    checkoutMode = CheckoutMode.ON_AGENT

    vcs {
        root(RepoVcs)
        cleanCheckout = true
    }

    steps {
        // step 0: force the requested commit + deterministic submodules
        script {
            name = "Checkout specific commit"
            scriptContent = """
                set -eu
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
        // step 1: build reproducible zip
        maven {
            name = "Create maven package"
            goals = "-Dmaven.test.skip=true clean package"
        }
    }

    triggers { vcs { } }
    features { perfmon { } }
})

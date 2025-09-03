import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.CheckoutMode
import jetbrains.buildServer.configs.kotlin.ParameterDisplay

// Use a conservative DSL version your server likely supports.
// If your server is newer, it will still accept this.
version = "2022.04"

project {
    vcsRoot(RepoVcs)
    buildType(Build)
}

object RepoVcs : GitVcsRoot({
    id("RepoVcs")
    name = "reproducable-mvn-build"
    url = "https://github.com/Varshraghu98/reproducable-mvn-build.git"
    branch = "refs/heads/main"

    // Deterministic submodules at pinned SHAs
    param("teamcity.git.submoduleCheckout", "CHECKOUT")
    // Full history so any SHA can be fetched
    param("teamcity.git.clone.depth", "0")
})

object Build : BuildType({
    name = "Build"

    params {
        text(
                name = "env.GIT_COMMIT",
                value = "",
                label = "Commit SHA",
                display = ParameterDisplay.PROMPT,
                readOnly = false,
                allowEmpty = false,
                regex = "[a-f0-9]{7,40}",
                validationMessage = "Enter a valid git SHA (7–40 hex chars)"
        )
    }


    vcs {
        root(RepoVcs)

        // Put checkout mode here (inside the vcs block)
        checkoutMode = CheckoutMode.ON_AGENT

        // Ensure a clean workspace each build
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

        // step 1: build reproducible zip/jar
        maven {
            name = "Create maven package"
            goals = "-Dmaven.test.skip=true clean package"
        }
    }

    triggers { vcs {} }
    features { perfmon {} }
})
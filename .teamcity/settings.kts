import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.CheckoutMode

// Conservative DSL version for broad compatibility
version = "2020.2"

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

    // ✅ Publish the ZIP from target/
    // Use a wildcard so version bumps don’t require DSL edits.
    artifactRules = "target/repro-docs-*.zip"

    params {
        // No validation, just a plain parameter you will fill when running the build
        // text("env.GIT_COMMIT", "")  // if this overload causes issues, use param(...) below
        param("env.GIT_COMMIT", "")
    }

    vcs {
        root(RepoVcs)
        cleanCheckout = true
        // Preferred way:
        checkoutMode = CheckoutMode.ON_AGENT
        // If the line above errors on your server, comment it and use this fallback:
        // param("teamcity.checkoutMode", "ON_AGENT")
    }

    steps {
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
        maven {
            name = "Create maven package"
            goals = "-Dmaven.test.skip=true clean package"
        }
    }

    triggers { vcs {} }
    features { perfmon {} }
})
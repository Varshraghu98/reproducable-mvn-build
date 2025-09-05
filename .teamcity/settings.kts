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
    name = "reproducable-mvn-build (SSH)"
    url = "git@github.com:Varshraghu98/reproducable-mvn-build.git"  // SSH URL
    branch = "refs/heads/main"
    branchSpec = """
        +:refs/heads/*
        +:refs/tags/*
    """.trimIndent()

    // Full history so any arbitrary SHA can be checked out
    param("teamcity.git.clone.depth", "0")

    // Use an uploaded SSH key (Project Settings → SSH Keys)
    authMethod = uploadedKey {
        uploadedKey = "tc-release-bot" // <-- set to your key name
    }
})

object Build : BuildType({
    name = "Build"

    // Publish the ZIP produced by Maven (version-agnostic)
    artifactRules = "target/repro-docs-*.zip"

    params {
        // Plain parameter; set this when running the build (e.g., a full SHA)
        param("env.GIT_COMMIT", "")
    }

    vcs {
        root(RepoVcs)
        cleanCheckout = true
        checkoutMode = CheckoutMode.ON_AGENT
        // If ON_AGENT enum is unavailable on your server, use:
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
                echo ">> Now at: $(git rev-parse HEAD)"
            """.trimIndent()
        }

        maven {
            name = "Create maven package"
            goals = "-Dmaven.test.skip=true clean package"
        }

        script {
            name = "SHA256 checksums"
            scriptContent = """
                chmod +x scripts/print-sha256.sh
                OUTPUT_DIR=target DOCS_PATTERN="repro-docs-*.zip" ./scripts/print-sha256.sh
            """.trimIndent()
        }
    }

    triggers {
        vcs {}
    }

    features {
        perfmon {}
    }
})

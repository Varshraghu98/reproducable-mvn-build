import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.sshAgent
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot
import jetbrains.buildServer.configs.kotlin.CheckoutMode


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

    param("teamcity.git.clone.depth", "0")

    param("teamcity.git.useNativeSsh", "true")

    authMethod = uploadedKey {
        uploadedKey = "tc-release-bot" // <-- your key name
    }
})

object Build : BuildType({
    name = "Build"

    artifactRules = "target/repro-docs-*.zip"

    params {
        param("env.GIT_COMMIT", "")
    }

    vcs {
        root(RepoVcs)
        cleanCheckout = true
        checkoutMode = CheckoutMode.ON_AGENT
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
                chmod +x buildscripts/print-sha256.sh
                CHECKOUT_DIR="%teamcity.build.checkoutDir%" DOCS_PATTERN="repro-docs-*.zip" ./buildscripts/print-sha256.sh
            """.trimIndent()
        }
    }

    triggers {
        vcs {}
    }

    features {
        perfmon {}
        sshAgent {
            teamcitySshKey = "tc-release-bot"
        }
    }
})

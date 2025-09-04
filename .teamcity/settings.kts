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

        script {
            name = "Print SHA-256 of ZIP"
            scriptContent = """
             #!/usr/bin/env sh
        set -eu

        files=${'$'}(ls -1 target/repro-docs-*.zip 2>/dev/null || true)
        if [ -z "${'$'}files" ]; then
          echo "No ZIPs found under target/"; exit 1
        fi

        for f in ${'$'}files; do
          if command -v sha256sum >/dev/null 2>&1; then
            sum=${'$'}(sha256sum "${'$'}f" | cut -d ' ' -f1)
          elif command -v shasum >/dev/null 2>&1; then
            sum=${'$'}(shasum -a 256 "${'$'}f" | cut -d ' ' -f1)
          elif command -v openssl >/dev/null 2>&1; then
            sum=${'$'}(openssl dgst -sha256 -r "${'$'}f" | cut -d ' ' -f1)
          else
            echo "No SHA-256 tool found (sha256sum/shasum/openssl)"; exit 1
          fi
          echo "SHA256(${ '$' }f) = ${ '$' }sum"
          printf "%s  %s\n" "${'$'}sum" "${'$'}(basename "${'$'}f")" > "${'$'}f.sha256"
        done
        """.trimIndent()
        }
    }

    triggers { vcs {} }
    features { perfmon {} }
})
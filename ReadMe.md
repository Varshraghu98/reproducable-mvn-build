# Assignment Solution — Reproducible Maven Build

The solution to the assignment is as follows:


## Repositories

- Parent repo (reproducible build):  
  https://github.com/Varshraghu98/reproducable-mvn-build

- Release notes repo:  
  https://github.com/Varshraghu98/release-notes



## TeamCity Setup

The TeamCity setup consists of the following jobs:

### 1. Fetch Release Notes
- Fetches the latest release notes from the marketing URL.
- Creates a manifest file with fetch details and the SHA256 of the release notes.
- Commits the fetched file into the release-notes repo:  
  https://github.com/Varshraghu98/release-notes
- Produces an artifact `notes-sha.txt` containing the latest commit SHA of the release-notes repo.

**Note:** For demonstration purposes, the marketing URL is:  
https://downloads.mysql.com/docs/mysql-9.0-relnotes-en.pdf



### 2. Vendor Release Notes
- Depends on the Fetch job.
- Reads the `notes-sha.txt` artifact to obtain the commit SHA of the release-notes repo.
- Checks out that commit from the release-notes repo.
- Syncs the latest release notes and manifest file into the parent repo.
- The parent repo now contains the updated release notes.

**Note:** Fetch + Vendoring are manual jobs that developers can run when they want to update to the latest release notes.



### 3. Reproducible Maven Build
- The main reproducible build job.
- Checks out the parent repo at the specified commit (`env.commitHash`).
    - If not set, it defaults to the latest commit on the branch.
- Runs the Maven build to produce a deterministic archive.
- The generated `.zip` file includes:
    - Javadocs
    - Vendored release notes
    - Manifest file (with provenance details)
- The SHA256 hash of the ZIP is calculated and printed in the build logs for verification.



## Outcome

- The `.zip` artifact is **byte-for-byte reproducible** when built from the same commit.
- Only the **commit hash** is required as input to reproduce the artifact.
- Verification can be done by comparing the SHA256 values of builds from the same commit.

### Teamcity Server Configuration

The TeamCity server runs from  
`jetbrains/teamcity-server:2025.07.1` with the following setup:

- **Pinned server image** to ensure consistent version
- **Exposed web UI** at `http://localhost:8111`

This ensures a stable, reproducible TeamCity server environment


### Teamcity Agent Configuration

The TeamCity build agent is based on  
`jetbrains/teamcity-minimal-agent:2025.07.1-linux` and extended with:

- **Pinned JDK 17** 
- **Pinned Maven 3.9.11**
- **Essential tools installed**
- Runs as the standard **`buildagent`** user (non-root)

This configuration ensures consistent tools and environment across all builds,  
making the Maven archive **byte-for-byte reproducible**.





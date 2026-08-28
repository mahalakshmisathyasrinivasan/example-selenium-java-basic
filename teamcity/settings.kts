import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.triggers.schedule

/*
    Sample TeamCity pipeline (Kotlin DSL) for executing Applitools Eyes tests
    on the Ultrafast Grid.

    Assumes:
      - Java/Maven project using eyes-selenium / eyes-playwright / eyes-appium SDK
      - Tests tagged/grouped so they can be run as a distinct build config
      - APPLITOOLS_API_KEY stored as a TeamCity secure parameter (never in plain text)

    Drop this into .teamcity/settings.kts (or import as a subproject) and adjust
    the VCS root ID, build steps, and parameter names to match your project.
*/

version = "2024.03"

project {
    buildType(ApplitoolsVisualTests)
}

object ApplitoolsVisualTests : BuildType({
    name = "Applitools Visual Regression Tests"
    description = "Runs Applitools Eyes tests against Ultrafast Grid and reports results back to the batch"

    artifactRules = """
        target/surefire-reports/**/* => test-reports
        target/site/**/* => site
    """.trimIndent()

    params {
        // Secure parameter - configure the actual value in TeamCity UI, not here
        password("env.APPLITOOLS_API_KEY", "%vault:applitools/api-key%")

        // Groups related test runs into a single Applitools batch for review
        param("env.APPLITOOLS_BATCH_ID", "%teamcity.build.id%")
        param("env.APPLITOOLS_BATCH_NAME", "%system.teamcity.buildConfName% - %teamcity.build.branch%")

        // Server URL - only needed for Applitools On-Premise / dedicated tenants
        // param("env.APPLITOOLS_SERVER_URL", "https://eyes.yourcompany.applitools.com")

        param("teamcity.build.branch.filter", "+:*")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        maven {
            name = "Install dependencies"
            goals = "clean install -DskipTests"
            runnerArgs = "-B"
        }

        maven {
            name = "Run Applitools Eyes tests"
            goals = "test"
            runnerArgs = "-Dtest=**/*VisualTest -B"

            // Fail fast is usually undesirable for visual tests since you want
            // the full batch to complete and populate the Applitools dashboard
            param("teamcity.build.failOnErrorMessages", "false")
        }

        script {
            name = "Poll Applitools batch status (optional gate)"
            scriptContent = """
                #!/bin/bash
                set -e
                echo "Batch ID: %env.APPLITOOLS_BATCH_ID%"

                # Calls the real "List batch results" API to check for failed/unresolved
                # tests before letting the pipeline proceed to deploy. Requires the batch
                # to be closed (all tests finished) before this check is meaningful.
                # Docs: https://applitools.com/docs/eyes/reference/server-api/batches/list-batch-results
                RESPONSE=${'$'}(curl -s -H "X-Eyes-Api-Key: %env.APPLITOOLS_API_KEY%" \
                  "https://eyes.applitools.com/api/v1/batches/%env.APPLITOOLS_BATCH_ID%?statsOnly=true")

                echo "${'$'}RESPONSE"

                FAILED=${'$'}(echo "${'$'}RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['statistics']['failed'])")
                UNRESOLVED=${'$'}(echo "${'$'}RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['statistics']['unresolved'])")

                if [ "${'$'}FAILED" != "0" ] || [ "${'$'}UNRESOLVED" != "0" ]; then
                  echo "##teamcity[buildStatus status='FAILURE' text='Applitools batch has failed=${'$'}FAILED unresolved=${'$'}UNRESOLVED diffs']"
                  exit 1
                fi
            """.trimIndent()
        }
    }

    triggers {
        vcs {
            branchFilter = "+:*"
        }
        // Optional nightly full-regression run across all viewports/browsers
        schedule {
            schedulingPolicy = daily {
                hour = 2
            }
            branchFilter = "+:<default>"
            triggerBuild = always()
            withPendingChangesOnly = false
        }
    }

    failureConditions {
        errorMessage = true
        // Don't fail the whole build on non-zero exit if you want the
        // Applitools dashboard step above to be the actual gate
        executionTimeoutMin = 30
    }

    requirements {
        // Pin to agents with the browser/driver binaries your SDK needs,
        // e.g. Chrome for Selenium/Playwright local fallback runs
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

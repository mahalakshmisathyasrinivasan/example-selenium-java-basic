import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.maven
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.schedule
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

version = "2026.1"

project {

    buildType(ApplitoolsVisualRegressionTestsV2)
    buildType(ApplitoolsVisualTests)
}

object ApplitoolsVisualRegressionTestsV2 : BuildType({
    name = "Applitools Visual Regression Tests v2"

    params {
        password("env.APPLITOOLS_API_KEY", "credentialsJSON:986ce0c1-6420-4913-859e-c36d7640514e")
    }

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        maven {
            name = "Install dependencies"
            id = "Install_dependencies"
            goals = "clean install -DskipTests"
            runnerArgs = "-B"
        }
        script {
            name = "Clear stale ChromeDriver"
            id = "Clear_stale_ChromeDriver"
            scriptContent = """
                #!/bin/bash
                echo "Current user: ${'$'}(whoami)"
                echo "Looking for chromedriver installations..."
                
                FOUND=${'$'}(find / -xdev -name "chromedriver" -type f 2>/dev/null)
                echo "Found paths:"
                echo "${'$'}FOUND"
                
                for path in ${'$'}FOUND; do
                  echo "Permissions on ${'$'}path:"
                  ls -la "${'$'}path"
                  echo "Attempting removal of ${'$'}path..."
                  if rm "${'$'}path" 2>/tmp/rm_err.txt; then
                    echo "  Removed successfully (no sudo needed)"
                  else
                    echo "  Direct rm failed: ${'$'}(cat /tmp/rm_err.txt)"
                    echo "  Trying with sudo..."
                    if sudo rm -f "${'$'}path" 2>/tmp/sudo_err.txt; then
                      echo "  Removed successfully with sudo"
                    else
                      echo "  sudo rm also failed: ${'$'}(cat /tmp/sudo_err.txt)"
                    fi
                  fi
                done
                
                echo "Clearing Selenium Manager caches..."
                rm -rf ~/.cache/selenium 2>/dev/null
                sudo rm -rf /root/.cache/selenium 2>/dev/null
                rm -rf /home/*/.cache/selenium 2>/dev/null
                
                echo "Final check - remaining chromedriver files:"
                find / -xdev -name "chromedriver" -type f 2>/dev/null
                
                echo "Done."
            """.trimIndent()
        }
        maven {
            name = "Run Applitools Eyes tests"
            id = "Run_Applitools_Eyes_tests"
            goals = "test"
            runnerArgs = "-Dtest=**/EyesTest -B"
        }
    }
})

object ApplitoolsVisualTests : BuildType({
    name = "Applitools Visual Regression Tests"
    description = "Runs Applitools Eyes tests against Ultrafast Grid and reports results back to the batch"

    artifactRules = """
        target/surefire-reports/**/* => test-reports
        target/site/**/* => site
    """.trimIndent()

    params {
        param("env.APPLITOOLS_BATCH_ID", "%teamcity.build.id%")
        password("env.APPLITOOLS_API_KEY", "credentialsJSON:22ba65da-83c7-4b6d-b223-aeb6443c69da")
        param("teamcity.build.branch.filter", "+:*")
        param("env.APPLITOOLS_BATCH_NAME", "%system.teamcity.buildConfName% - %teamcity.build.branch%")
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
            goals = "clean test-compile exec:exec@run-the-tests"
            runnerArgs = "-B -Dexec.classpathScope=test"
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
        }
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
        executionTimeoutMin = 30
        errorMessage = true
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})

def call(String webhookUrl) {

    String result = currentBuild.currentResult ?: "UNKNOWN"
    String buildUrl = env.BUILD_URL

    // Who triggered the build?
    def causes = currentBuild.rawBuild.getCauses()
    String triggeredBy = causes?.collect { it.getShortDescription() }.join(", ")

    // Get dynamic reason based on build result
    String reason = getReason(result)

    // Message Body
    def message = """
*Jenkins Build ${result}*

Job: ${env.JOB_NAME}
Build Number: ${env.BUILD_NUMBER}
Triggered By: ${triggeredBy}

*Build Result:* ${result}
*Reason:* ${reason}

🔗 Build URL: ${buildUrl}
"""

    sendMessageToGoogleChat(webhookUrl, message)
}

//
// MASTER REASON CONTROLLER
//
String getReason(String result) {

    switch (result) {

        case "FAILURE":
            return getFailureReason()

        case "UNSTABLE":
            return getUnstableReason()

        case "ABORTED":
            return "Build was manually aborted by user or system."

        case "NOT_BUILT":
            return getNotBuiltReason()

        case "SUCCESS":
            return "Build completed successfully."

        default:
            return "No specific reason found."
    }
}

//
// FAILURE REASON
//
String getFailureReason() {
    try {
        // Real exception if available
        def failure = currentBuild.rawBuild.getExecution().getCauseOfFailure()
        if (failure) {
            return failure.getMessage()
        }

        // fallback: last 15 lines of console log
        return currentBuild.rawBuild.getLog(15).join("\n")
    } catch (e) {
        return "Unable to detect failure reason."
    }
}

//
// UNSTABLE REASON
//
String getUnstableReason() {
    try {
        def actions = currentBuild.rawBuild.getActions()
        def testResult = actions.find { it instanceof hudson.tasks.junit.TestResultAction }

        if (testResult) {
            return "Test Failures: ${testResult.failCount}"
        }

        return "Marked unstable due to test failures, warnings, or quality gate."
    } catch (e) {
        return "Unable to detect unstable reason."
    }
}

//
// NOT_BUILT REASON — DYNAMIC
//
String getNotBuiltReason() {
    try {
        def log = currentBuild.rawBuild.getLog(250)

        // Stage skipped by when condition
        def skippedWhen = log.find { it =~ /Stage ".*" skipped due to when condition/ }
        if (skippedWhen) return "Stage skipped due to 'when' condition."

        // Pipeline early exit
        def earlyExit = log.find { it =~ /(Returning early|Early exit|Exiting pipeline)/ }
        if (earlyExit) return "Pipeline exited early before stage execution."

        // Node/Agent allocation issue
        def noAgent = log.find { it =~ /(Agent.*could not be allocated|No node available|Executor unavailable)/ }
        if (noAgent) return "Agent/node allocation failure."

        // SCM checkout skipped
        def scmSkip = log.find { it =~ /(Skipping checkout|SCM skipped|No changes detected)/ }
        if (scmSkip) return "SCM checkout skipped (no changes or disabled)."

        // Parallel branch skip
        def parallelSkip = log.find { it =~ /(Not executing.*was skipped|Parallel branch)/ }
        if (parallelSkip) return "Parallel branch marked NOT_BUILT due to sibling failure."

        // Nothing matched
        return "Build marked NOT_BUILT due to skipped stage or unmet conditions."
    }
    catch (Exception e) {
        return "Unable to detect NOT_BUILT reason."
    }
}

//
// SEND MESSAGE TO GOOGLE CHAT
//
void sendMessageToGoogleChat(String webhookUrl, String text) {
    def payload = """{ "text": "${text.replace("\"","'")}" }"""

    sh """
        curl -X POST -H 'Content-Type: application/json' \
        -d '${payload}' \
        '${webhookUrl}'
    """
}

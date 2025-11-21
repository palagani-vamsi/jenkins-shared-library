def call(String status, String webhookUrl) {

    String result = currentBuild.currentResult ?: "UNKNOWN"
    String buildUrl = env.BUILD_URL

    // Who triggered the build?
    def causes = currentBuild.rawBuild.getCauses()
    String triggeredBy = causes?.collect { it.getShortDescription() }.join(", ")

    // -----------------------------
    // OVERALL REASON DETECTOR
    // -----------------------------
    String reason = getReason(result)

    // -----------------------------
    // Create Google Chat Message
    // -----------------------------
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
// Detect reason for FAILURE / UNSTABLE / ABORTED / SUCCESS / NOT_BUILT
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
            return "Build was skipped due to unmet conditions or disabled stages."

        case "SUCCESS":
            return "Build completed successfully."

        default:
            return "No specific reason found."
    }
}

//
// Extract real FAILURE reason
//
String getFailureReason() {
    try {
        def failure = currentBuild.rawBuild.getExecution().getCauseOfFailure()
        if (failure) {
            return failure.getMessage()
        }

        // fallback → last 10 log lines
        return currentBuild.rawBuild.getLog(15).join("\n")
    } catch (e) {
        return "Unable to detect failure reason."
    }
}

//
// Extract UNSTABLE reason
//
String getUnstableReason() {
    try {
        def actions = currentBuild.rawBuild.getActions()
        def testResult = actions.find { it instanceof hudson.tasks.junit.TestResultAction }

        if (testResult) {
            return "Test Failures: ${testResult.failCount}"
        }

        return "Marked unstable due to failed tests, warnings, or quality gate."
    } catch (e) {
        return "Unable to detect unstable reason."
    }
}

//
// Send message to Google Chat
//
void sendMessageToGoogleChat(String webhookUrl, String text) {
    def payload = """{ "text": "${text.replace("\"","'")}" }"""

    sh """
        curl -X POST -H 'Content-Type: application/json' \
        -d '${payload}' \
        '${webhookUrl}'
    """
}

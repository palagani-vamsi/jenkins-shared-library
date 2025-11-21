def call(String webhookUrl) {

    String result = currentBuild.currentResult ?: "UNKNOWN"
    String buildUrl = env.BUILD_URL

    // Who triggered the build?
    def causes = currentBuild.rawBuild.getCauses()
    String triggeredBy = causes?.collect { it.getShortDescription() }.join(", ")

    // Dynamic reason
    String reason = getReason(result)

    // Build Message
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
// MASTER REASON HANDLER
//
String getReason(String result) {

    switch (result) {

        case "FAILURE":
            return getFailureReason()

        case "UNSTABLE":
            return getUnstableReason()

        case "ABORTED":
            return "Build was manually aborted."

        case "NOT_BUILT":
            return getNotBuiltReason()

        case "SUCCESS":
            return "Build completed successfully."

        default:
            return "Reason not detected."
    }
}

//
// **ACCURATE FAILURE REASON**
// 5 lines before error + error line + 15 lines after
//
String getFailureReason() {
    try {
        def log = currentBuild.rawBuild.getLog(500) // more lines available

        // Find the error line index
        int index = log.findIndexOf { line ->
            line =~ /(ERROR|Exception|Caused by|Failed|Traceback)/
        }

        if (index == -1) {
            // Fallback to last 20 lines
            return log.takeRight(20).join("\n")
        }

        // Get 5 lines before and 15 after
        int start = Math.max(0, index - 5)
        int end = Math.min(log.size(), index + 15)

        def snippet = log.subList(start, end)

        return snippet.join("\n")
    }
    catch (Exception e) {
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

        return "Marked UNSTABLE due to warnings or quality gate failure."
    } catch (Exception e) {
        return "Unable to detect unstable reason."
    }
}

//
// DYNAMIC NOT_BUILT REASON
//
String getNotBuiltReason() {
    try {
        def log = currentBuild.rawBuild.getLog(200)

        if (log.find { it =~ /skipped due to when condition/ })
            return "Stage skipped due to 'when' condition."

        if (log.find { it =~ /Returning early|exiting pipeline/ })
            return "Pipeline exited early."

        if (log.find { it =~ /(could not be allocated|No node available)/ })
            return "Agent/node allocation failure."

        if (log.find { it =~ /(Skipping checkout|SCM skipped)/ })
            return "SCM checkout skipped."

        return "Pipeline marked NOT_BUILT (stage skipped)."

    } catch (Exception e) {
        return "Unable to detect NOT_BUILT reason."
    }
}

//
// Send message
//
void sendMessageToGoogleChat(String webhookUrl, String text) {
    def payload = """{ "text": "${text.replace("\"","'")}" }"""

    sh """
        curl -X POST -H 'Content-Type: application/json' \
        -d '${payload}' \
        '${webhookUrl}'
    """
}

def call(String webhookUrl, String status) {

    // Always create a complete serializable cause description
    def causes = currentBuild.getBuildCauses()
    def triggeredBy = causes.collect { it.shortDescription }.join(', ')

    // Get dynamic reason safely
    def reason = currentBuild.rawBuild?.getLog(50)?.join('\n') ?: "No reason available"

    // Build link
    def buildUrl = env.RUN_DISPLAY_URL ?: "${env.BUILD_URL}"

    // Prepare message
    def message = """
Jenkins Build *${status}*

Job: *${env.JOB_NAME}*
Build Number: *${env.BUILD_NUMBER}*
Triggered By: *${triggeredBy}*

*Build Result:* ${status}
*Reason:* ${status == 'SUCCESS' ? 'Build completed successfully' : reason}

🔗 *Build URL:* ${buildUrl}
"""

    try {
        withEnv(["HOOK_URL=${webhookUrl}"]) {
            sh '''
                curl -X POST "$HOOK_URL" \
                -H "Content-Type: application/json" \
                -d @- <<EOF
                {
                  "text": "'"${message.replace("\n", "\\n")}"'"
                }
EOF
            '''
        }
    } catch (err) {
        echo "Google Chat notification failed: ${err}"
    }
}

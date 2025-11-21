import org.example.utils.GoogleChat

def call(String webhookUrl, String message) {
    GoogleChat.send(webhookUrl, message)
}

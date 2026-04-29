package com.example.antipoaching

import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

object EmailSender {

    private const val EMAIL_ADDRESS = "mdfarhaan1365@gmail.com"
    private const val EMAIL_PASSWORD = "zkwa aydr ktya ujvm" // App Password
    private const val TO_EMAIL = "mdthariq13@gmail.com"

    fun sendEmailAlert(imagePath: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        Thread {
            try {
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", "smtp.gmail.com")
                    put("mail.smtp.port", "465")
                    put("mail.smtp.socketFactory.port", "465")
                    put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                }

                val session = Session.getInstance(props, object : javax.mail.Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(EMAIL_ADDRESS, EMAIL_PASSWORD)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(EMAIL_ADDRESS))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL))
                    subject = "🚨 Poacher/Hunter/Gun Detected Alert"
                }

                val multipart = MimeMultipart()

                // Text part
                val textPart = MimeBodyPart().apply {
                    setText("Alert: A poacher, hunter, or gun was detected. See the attached snapshot.")
                }
                multipart.addBodyPart(textPart)

                // Image part
                val file = File(imagePath)
                if (file.exists()) {
                    val imagePart = MimeBodyPart().apply {
                        val source = FileDataSource(file)
                        dataHandler = DataHandler(source)
                        fileName = file.name
                    }
                    multipart.addBodyPart(imagePart)
                }

                message.setContent(multipart)
                Transport.send(message)
                
                Handler(Looper.getMainLooper()).post {
                    onSuccess()
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onError(e)
                }
            }
        }.start()
    }
}

use std::env;

use lettre::message::Mailbox;
use lettre::transport::smtp::authentication::Credentials;
use lettre::{AsyncSmtpTransport, AsyncTransport, Message, Tokio1Executor};

use crate::errors::ApiError;

pub struct EmailService {
    mailer: AsyncSmtpTransport<Tokio1Executor>,
    sender_email: String,
}

impl EmailService {
    pub fn from_env() -> Result<Self, ApiError> {
        dotenv::dotenv().ok();

        let smtp_server = env::var("SMTP_SERVER")
            .map_err(|_| ApiError::InternalServerError("SMTP_SERVER is not configured".to_string()))?;
        let smtp_port = env::var("SMTP_PORT")
            .ok()
            .and_then(|value| value.parse::<u16>().ok())
            .unwrap_or(587);
        let smtp_username = env::var("SMTP_USERNAME")
            .map_err(|_| ApiError::InternalServerError("SMTP_USERNAME is not configured".to_string()))?;
        let smtp_password = env::var("SMTP_PASSWORD")
            .map_err(|_| ApiError::InternalServerError("SMTP_PASSWORD is not configured".to_string()))?;
        let sender_email = env::var("SENDER_EMAIL")
            .unwrap_or_else(|_| smtp_username.clone());
        let smtp_security = env::var("SMTP_SECURITY")
            .unwrap_or_else(|_| "auto".to_string())
            .to_ascii_lowercase();

        let credentials = Credentials::new(smtp_username, smtp_password);
        let builder = match smtp_security.as_str() {
            "starttls" => AsyncSmtpTransport::<Tokio1Executor>::starttls_relay(&smtp_server),
            "tls" | "ssl" | "smtps" => AsyncSmtpTransport::<Tokio1Executor>::relay(&smtp_server),
            "plain" | "none" => Ok(AsyncSmtpTransport::<Tokio1Executor>::builder_dangerous(&smtp_server)),
            _ => {
                if smtp_port == 587 {
                    AsyncSmtpTransport::<Tokio1Executor>::starttls_relay(&smtp_server)
                } else if smtp_port == 465 {
                    AsyncSmtpTransport::<Tokio1Executor>::relay(&smtp_server)
                } else {
                    Ok(AsyncSmtpTransport::<Tokio1Executor>::builder_dangerous(&smtp_server))
                }
            }
        }
        .map_err(|error| {
            ApiError::InternalServerError(format!("Failed to configure SMTP transport: {}", error))
        })?;

        let mailer = builder
            .port(smtp_port)
            .credentials(credentials)
            .build();

        Ok(Self {
            mailer,
            sender_email,
        })
    }

    pub async fn send_code_email(
        &self,
        recipient_email: &str,
        subject: &str,
        purpose: &str,
        code: &str,
        expires_in_minutes: i64,
    ) -> Result<(), ApiError> {
        let from = self.parse_mailbox(&self.sender_email)?;
        let to = self.parse_mailbox(recipient_email)?;

        let body = format!(
            "Hello,\n\nYour {} code is: {}\n\nThis code expires in {} minutes.\n\nIf you did not request this email, you can ignore it.\n",
            purpose, code, expires_in_minutes
        );

        let message = Message::builder()
            .from(from)
            .to(to)
            .subject(subject)
            .body(body)
            .map_err(|error| {
                ApiError::InternalServerError(format!("Failed to build email message: {}", error))
            })?;

        self.mailer
            .send(message)
            .await
            .map_err(|error| ApiError::InternalServerError(format!("Failed to send email: {}", error)))?;

        Ok(())
    }

    fn parse_mailbox(&self, email: &str) -> Result<Mailbox, ApiError> {
        email.parse::<Mailbox>().map_err(|error| {
            ApiError::InternalServerError(format!("Invalid email address '{}': {}", email, error))
        })
    }
}
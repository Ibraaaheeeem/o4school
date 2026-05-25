use chrono::{NaiveDateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

/// WhatsAppMessaging table - WhatsApp message logs
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct WhatsAppMessaging {
    pub id: Uuid,
    pub school_id: Uuid,
    pub recipient_phone: String,
    pub message_body: String,
    pub status: String,
    pub sent_at: Option<NaiveDateTime>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// WhatsAppTemplates table - WhatsApp message templates
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct WhatsAppTemplate {
    pub id: Uuid,
    pub school_id: Uuid,
    pub name: String,
    pub template_body: String,
    pub is_for_broadcast: bool,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// SmsMessaging table - SMS message logs
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct SmsMessaging {
    pub id: Uuid,
    pub school_id: Uuid,
    pub recipient_phone: String,
    pub message_body: String,
    pub status: String,
    pub sent_at: Option<NaiveDateTime>,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// InternalMessages table - Internal messaging
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct InternalMessage {
    pub id: Uuid,
    pub school_id: Uuid,
    pub sender_id: Uuid,
    pub subject: String,
    pub body: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// InternalMessageThreads table - Message threads
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct InternalMessageThread {
    pub id: Uuid,
    pub school_id: Uuid,
    pub subject: String,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

/// InternalMessageParticipants table - Thread participants
#[derive(Debug, Clone, Serialize, Deserialize, sqlx::FromRow)]
pub struct InternalMessageParticipant {
    pub id: Uuid,
    pub school_id: Uuid,
    pub thread_id: Uuid,
    pub user_id: Uuid,
    pub created_at: NaiveDateTime,
    pub updated_at: NaiveDateTime,
    pub is_active: bool,
}

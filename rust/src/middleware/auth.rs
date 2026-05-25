use actix_web::dev::{Service, ServiceRequest, ServiceResponse, Transform};
use actix_web::{Error, HttpMessage, HttpRequest};
use actix_web::error::ErrorUnauthorized;
use actix_web::dev::Payload;
use futures_util::future::{ok, Ready, LocalBoxFuture, ready};
use futures_util::FutureExt;
use std::rc::Rc;
use uuid::Uuid;

#[derive(Clone, Debug)]
pub struct UserContext {
    pub user_id: Uuid,
    pub email: String,
}

pub struct AuthMiddleware;

impl AuthMiddleware {
    pub fn new() -> Self {
        AuthMiddleware
    }
}

pub struct AuthMiddlewareService<S> {
    service: Rc<S>,
}

impl<S, B> Transform<S, ServiceRequest> for AuthMiddleware
where
    S: Service<ServiceRequest, Response = ServiceResponse<B>, Error = Error> + 'static,
    B: 'static,
{
    type Response = ServiceResponse<B>;
    type Error = Error;
    type Transform = AuthMiddlewareService<S>;
    type InitError = ();
    type Future = Ready<Result<Self::Transform, Self::InitError>>;

    fn new_transform(&self, service: S) -> Self::Future {
        ready(Ok(AuthMiddlewareService { service: Rc::new(service) }))
    }
}

impl<S, B> Service<ServiceRequest> for AuthMiddlewareService<S>
where
    S: Service<ServiceRequest, Response = ServiceResponse<B>, Error = Error> + 'static,
    B: 'static,
{
    type Response = ServiceResponse<B>;
    type Error = Error;
    type Future = LocalBoxFuture<'static, Result<Self::Response, Self::Error>>;

    fn poll_ready(&self, cx: &mut std::task::Context<'_>) -> std::task::Poll<Result<(), Self::Error>> {
        self.service.poll_ready(cx)
    }

    fn call(&self, mut req: ServiceRequest) -> Self::Future {
        let srv = Rc::clone(&self.service);

        async move {
            // Try extract Authorization header
            if let Some(hv) = req.headers().get("Authorization") {
                if let Ok(s) = hv.to_str() {
                    if s.starts_with("Bearer ") {
                        let token = &s[7..];
                        // Decode token into claims using jsonwebtoken
                        let secret = std::env::var("JWT_SECRET").unwrap_or_else(|_| "your-secret-key-change-in-production".to_string());
                        if let Ok(token_data) = jsonwebtoken::decode::<crate::services::auth_service::JwtClaims>(
                            token,
                            &jsonwebtoken::DecodingKey::from_secret(secret.as_ref()),
                            &jsonwebtoken::Validation::default(),
                        ) {
                            let claims = token_data.claims;
                            let ctx = UserContext { user_id: claims.user_id, email: claims.email };
                            req.extensions_mut().insert(ctx);
                        }
                    }
                }
            }

            let res = srv.call(req).await?;
            Ok(res)
        }
        .boxed_local()
    }
}

impl actix_web::FromRequest for UserContext {
    type Error = Error;
    type Future = Ready<Result<Self, Self::Error>>;

    fn from_request(req: &HttpRequest, _payload: &mut Payload) -> Self::Future {
        if let Some(ctx) = req.extensions().get::<UserContext>() {
            return ok(ctx.clone());
        }
        // No auth info available
        ready(Err(ErrorUnauthorized("Authentication required")))
    }
}

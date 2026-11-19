use aes::Aes256;
use base64::{decode, encode};
use block_modes::block_padding::Pkcs7;
use block_modes::{BlockMode, Cbc};
use rand::rngs::OsRng;
use rand::Rng;
use std::str;
use std::str::Utf8Error;

mod flow_control;

#[derive(Debug)]
pub enum CryptorError {
    IllegalArgumentError(String),
    Utf8Error(Utf8Error),
    BlockModeError(block_modes::BlockModeError),
    InvalidKeyIvLength(block_modes::InvalidKeyIvLength),
    DecodeError(base64::DecodeError),
}

impl From<Utf8Error> for CryptorError {
    fn from(value: Utf8Error) -> Self {
        CryptorError::Utf8Error(value)
    }
}

impl From<block_modes::BlockModeError> for CryptorError {
    fn from(value: block_modes::BlockModeError) -> Self {
        CryptorError::BlockModeError(value)
    }
}

impl From<block_modes::InvalidKeyIvLength> for CryptorError {
    fn from(value: block_modes::InvalidKeyIvLength) -> Self {
        CryptorError::InvalidKeyIvLength(value)
    }
}

impl From<base64::DecodeError> for CryptorError {
    fn from(value: base64::DecodeError) -> Self {
        CryptorError::DecodeError(value)
    }
}

// Create an alias for the CBC mode with AES-256 encryption
type Aes256Cbc = Cbc<Aes256, Pkcs7>;

/// Encrypt the input data
fn encrypt(json: &str, key: &[u8], iv: &[u8]) -> Result<String, CryptorError> {
    let cipher = Aes256Cbc::new_from_slices(key, iv)?;
    let ciphertext = cipher.encrypt_vec(json.as_bytes());
    Ok(encode(&ciphertext)) // Base64 encode the encrypted data
}

/// Decrypt the input data
fn decrypt(encrypted_data: &str, key: &[u8], iv: &[u8]) -> Result<String, CryptorError> {
    let cipher = Aes256Cbc::new_from_slices(key, iv)?;
    let decrypted_ciphertext = cipher.decrypt_vec(&decode(encrypted_data)?)?;
    Ok(str::from_utf8(&decrypted_ciphertext)?.to_string())
}

#[allow(unused)]
fn generate_key() -> [u8; 32] {
    let mut key = [0u8; 32];
    OsRng.fill(&mut key);
    key
}

#[allow(unused)]
fn generate_iv() -> [u8; 16] {
    let mut iv = [0u8; 16];
    OsRng.fill(&mut iv);
    iv
}

const FIXED_KEY: [u8; 32] = [
    110, 189, 91, 172, 236, 119, 123, 236, 183, 12, 74, 4, 185, 255, 104, 227, 34, 158, 111, 113,
    243, 164, 202, 64, 147, 47, 83, 84, 141, 64, 47, 228,
];
const FIXED_IV: [u8; 16] = [
    63, 32, 120, 117, 41, 120, 122, 236, 90, 71, 47, 7, 176, 239, 75, 234,
];

/// Encrypt the input data
pub fn encrypt_with_fixed_key_and_iv(json: &str) -> Result<String, CryptorError> {
    return encrypt(json, &FIXED_KEY, &FIXED_IV);
}

/// Decrypt the input data
pub fn decrypt_with_fixed_key_and_iv(encrypted: &str) -> Result<String, CryptorError> {
    return decrypt(encrypted, &FIXED_KEY, &FIXED_IV);
}

#[cfg(target_os = "ios")]
pub mod ios {
    use super::decrypt_with_fixed_key_and_iv;
    use super::encrypt_with_fixed_key_and_iv;
    use std::ffi::{CStr, CString};
    use std::os::raw::c_char;

    #[no_mangle]
    pub extern "C" fn encrypt(json: *const c_char) -> *mut c_char {
        let c_str = unsafe { CStr::from_ptr(json) };
        CString::new(encrypt_with_fixed_key_and_iv(c_str.to_str().unwrap()).unwrap())
            .unwrap()
            .into_raw()
    }

    #[no_mangle]
    pub extern "C" fn decrypt(encrypted: *const c_char) -> *mut c_char {
        let c_str = unsafe { CStr::from_ptr(encrypted) };
        CString::new(decrypt_with_fixed_key_and_iv(c_str.to_str().unwrap()).unwrap())
            .unwrap()
            .into_raw()
    }
}

#[cfg(any(target_os = "linux", target_os = "macos", target_os = "windows", target_os = "android"))]
#[allow(non_snake_case)]
pub mod java {
    extern crate jni;

    use self::jni::objects::JClass;
    use self::jni::JNIEnv;
    use super::decrypt_with_fixed_key_and_iv;
    use super::encrypt_with_fixed_key_and_iv;
    use super::CryptorError;
    use jni::errors::Error;
    use jni::objects::JString;
    use jni::sys::jboolean;
    use jni::sys::jint;
    use jni::sys::{jobject, jstring};

    static RUNTIME_EXCEPTION_CLASS: &str = "java/lang/RuntimeException";

    #[derive(Debug)]
    enum CryptorJavaError {
        JniError(jni::errors::Error),
        CryptorError(CryptorError),
    }

    impl From<jni::errors::Error> for CryptorJavaError {
        fn from(value: Error) -> Self {
            CryptorJavaError::JniError(value)
        }
    }

    impl From<CryptorError> for CryptorJavaError {
        fn from(value: CryptorError) -> Self {
            CryptorJavaError::CryptorError(value)
        }
    }

    fn encrypt_impl(env: &mut JNIEnv, json: JString) -> Result<jstring, CryptorJavaError> {
        let rust_str: String = env.get_string(&json)?.into();
        let encrypted_rust_str = encrypt_with_fixed_key_and_iv(rust_str.as_str())?;
        Ok(env.new_string(encrypted_rust_str)?.into_raw())
    }

    fn decrypt_impl(env: &mut JNIEnv, str: JString) -> Result<jstring, CryptorJavaError> {
        let rust_str: String = env.get_string(&str)?.into();
        let decrypted_rust_str = decrypt_with_fixed_key_and_iv(rust_str.as_str())?;
        Ok(env.new_string(decrypted_rust_str)?.into_raw())
    }

    fn throw_runtime_exception(env: &mut JNIEnv, message: String) -> jobject {
        env.throw_new(RUNTIME_EXCEPTION_CLASS, message.as_str())
            .expect(
                format!(
                    "Failed to throw {} with message: {}",
                    RUNTIME_EXCEPTION_CLASS,
                    message.as_str()
                )
                .as_str(),
            );
        std::ptr::null_mut()
    }

    #[no_mangle]
    pub unsafe extern "C" fn Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_encrypt(
        mut env: JNIEnv,
        _: JClass,
        json: JString,
    ) -> jstring {
        return match encrypt_impl(&mut env, json) {
            Ok(value) => value,
            Err(error) => match error {
                CryptorJavaError::JniError(error) => {
                    throw_runtime_exception(&mut env, format!("{:?}", error))
                }
                CryptorJavaError::CryptorError(error) => match error {
                    CryptorError::IllegalArgumentError(error) => {
                        throw_runtime_exception(&mut env, error)
                    }
                    CryptorError::Utf8Error(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::BlockModeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::InvalidKeyIvLength(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::DecodeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                },
            },
        };
    }

    #[no_mangle]
    pub unsafe extern "C" fn Java_poseidon_crypt_Cryptor_encrypt(
        mut env: JNIEnv,
        _: JClass,
        json: JString,
    ) -> jstring {
        return match encrypt_impl(&mut env, json) {
            Ok(value) => value,
            Err(error) => match error {
                CryptorJavaError::JniError(error) => {
                    throw_runtime_exception(&mut env, format!("{:?}", error))
                }
                CryptorJavaError::CryptorError(error) => match error {
                    CryptorError::IllegalArgumentError(error) => {
                        throw_runtime_exception(&mut env, error)
                    }
                    CryptorError::Utf8Error(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::BlockModeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::InvalidKeyIvLength(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::DecodeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                },
            },
        };
    }

    #[no_mangle]
    pub unsafe extern "C" fn Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_decrypt(
        mut env: JNIEnv,
        _: JClass,
        str: JString,
    ) -> jstring {
        return match decrypt_impl(&mut env, str) {
            Ok(value) => value,
            Err(error) => match error {
                CryptorJavaError::JniError(error) => {
                    throw_runtime_exception(&mut env, format!("{:?}", error))
                }
                CryptorJavaError::CryptorError(error) => match error {
                    CryptorError::IllegalArgumentError(error) => {
                        throw_runtime_exception(&mut env, error)
                    }
                    CryptorError::Utf8Error(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::BlockModeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::InvalidKeyIvLength(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::DecodeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                },
            },
        };
    }

    #[no_mangle]
    pub unsafe extern "C" fn Java_poseidon_crypt_Cryptor_decrypt(
        mut env: JNIEnv,
        _: JClass,
        str: JString,
    ) -> jstring {
        return match decrypt_impl(&mut env, str) {
            Ok(value) => value,
            Err(error) => match error {
                CryptorJavaError::JniError(error) => {
                    throw_runtime_exception(&mut env, format!("{:?}", error))
                }
                CryptorJavaError::CryptorError(error) => match error {
                    CryptorError::IllegalArgumentError(error) => {
                        throw_runtime_exception(&mut env, error)
                    }
                    CryptorError::Utf8Error(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::BlockModeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::InvalidKeyIvLength(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                    CryptorError::DecodeError(error) => {
                        throw_runtime_exception(&mut env, format!("{:?}", error))
                    }
                },
            },
        };
    }

    #[no_mangle]
    pub unsafe extern "C" fn Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_isPassableByFlowControl(
        mut env: JNIEnv,
        _: JClass,
        deviceId: JString,
        scale: jint,
    ) -> jboolean {

        fn is_passable(env: &mut JNIEnv, deviceId: JString, scale: jint) -> Result<bool, jni::errors::Error>{
            let device_id: String = env.get_string(&deviceId)?.into();
            let is_passable = super::flow_control::FlowControl::instance(device_id).is_passable(scale.max(0) as u32);
            Ok(is_passable)
        }
        
        is_passable(&mut env, deviceId, scale).unwrap_or(true) as jboolean
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_encrypt_decrypt() {
        let json = r#"{"name":"Alice","age":30}"#;

        let key = generate_key();
        let iv = generate_iv();

        let encrypted = encrypt(json, &key, &iv).unwrap();
        assert!(!encrypted.is_empty(), "The encrypted string should not be empty");

        let decrypted = decrypt(&encrypted, &key, &iv).unwrap();
        assert_eq!(decrypted, json, "The decrypted data should be the same as the original data");
    }

    #[test]
    fn test_encrypt_decrypt_with_fixed_key_and_iv() {
        let json = r#"{"name":"Alice","age":30}"#;

        let encrypted = encrypt_with_fixed_key_and_iv(json).unwrap();
        assert!(!encrypted.is_empty(), "The encrypted string should not be empty");

        let decrypted = decrypt_with_fixed_key_and_iv(&encrypted).unwrap();
        assert_eq!(decrypted, json, "The decrypted data should be the same as the original data");
    }

    #[test]
    fn test_generate_key_and_iv() {
        let key1 = generate_key();
        let key2 = generate_key();
        let iv1 = generate_iv();
        let iv2 = generate_iv();

        assert_eq!(key1.len(), 32, "The length of the AES-256 key should be 32 bytes");
        assert_eq!(iv1.len(), 16, "The length of the IV should be 16 bytes");

        assert_ne!(key1, key2, "The generated key should be random and should not be the same");
        assert_ne!(iv1, iv2, "The generated IV should be random and should not be the same");
    }
}

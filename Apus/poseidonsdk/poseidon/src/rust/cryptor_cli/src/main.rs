use cryptor::{decrypt_with_fixed_key_and_iv, encrypt_with_fixed_key_and_iv, CryptorError};
use std::fs::{read_to_string, File};
use std::io::{Error, Write};
use std::path::PathBuf;
use structopt::StructOpt;

#[derive(Debug)]
enum CryptorCliError {
    CryptorError(CryptorError),
    IoError(Error),
}

impl From<Error> for CryptorCliError {
    fn from(value: Error) -> Self {
        CryptorCliError::IoError(value)
    }
}

impl From<CryptorError> for CryptorCliError {
    fn from(value: CryptorError) -> Self {
        CryptorCliError::CryptorError(value)
    }
}

fn parse_args() -> Result<(), CryptorCliError> {
    let args = Args::from_args();
    if args.debug {
        dbg!(&args);
    }
    return match args.opt {
        Opt::Encrypt { input, output } => {
            let encrypted = encrypt_with_fixed_key_and_iv(read_to_string(input)?.as_str())?;
            Ok(File::create(output)?.write_all(encrypted.as_bytes())?)
        }
        Opt::Decrypt { input, output } => {
            let decrypted = decrypt_with_fixed_key_and_iv(read_to_string(input)?.as_str())?;
            Ok(File::create(output)
                .unwrap()
                .write_all(decrypted.as_bytes())?)
        }
    };
}

fn main() {
    if let Err(fusion_error) = parse_args() {
        println!(
            "{}",
            match fusion_error {
                CryptorCliError::CryptorError(cryptor_error) => match cryptor_error {
                    CryptorError::IllegalArgumentError(error) => error,
                    CryptorError::Utf8Error(error) => format!("{}", error),
                    CryptorError::BlockModeError(error) => format!("{}", error),
                    CryptorError::InvalidKeyIvLength(error) => format!("{}", error),
                    CryptorError::DecodeError(error) => format!("{}", error),
                },
                CryptorCliError::IoError(error) => format!("{}", error),
            }
        )
    }
}

#[derive(Debug, StructOpt)]
enum Opt {
    /// Encrypt
    Encrypt {
        /// Input file
        #[structopt(short, long, parse(from_os_str))]
        input: PathBuf,

        /// Output file
        #[structopt(short, long, parse(from_os_str))]
        output: PathBuf,
    },

    /// Decrypt
    Decrypt {
        /// Input file
        #[structopt(short, long, parse(from_os_str))]
        input: PathBuf,

        /// Output file
        #[structopt(short, long, parse(from_os_str))]
        output: PathBuf,
    },
}

#[derive(Debug, StructOpt)]
struct Args {
    #[structopt(subcommand)]
    opt: Opt,

    #[structopt(long, hidden = true)]
    debug: bool,
}

use crc32fast::Hasher;
use structopt::StructOpt;

fn crc32(input: &str) -> u32 {
    let mut hasher = Hasher::new();
    hasher.update(input.as_bytes());
    hasher.finalize()
}

fn main() {
    let args = Args::from_args();
    if args.debug {
        dbg!(&args);
    }
    println!("{}", crc32(&args.str));
}

#[derive(Debug, StructOpt)]
struct Args {
    #[structopt(short)]
    str: String,

    #[structopt(long, hidden = true)]
    debug: bool,
}

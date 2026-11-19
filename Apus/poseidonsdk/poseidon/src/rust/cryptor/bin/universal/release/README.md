# Cryptor for iOS

# 如何构建 libPoseidonCypt.a 供iOS使用

cd 到 poseidon/src/rust/cryptor 目录后执行如下命令:

1. `$ rustup target install aarch64-apple-ios`
2. `$ rustup target install x86_64-apple-ios`
3. `$ rustup target install aarch64-apple-ios-sim`
4. `$ cargo install cargo-lipo`
5. `$ cargo lipo --release --targets aarch64-apple-ios,x86_64-apple-ios,aarch64-apple-ios-sim`
6. `$ cp target/universal/release/libPoseidonCypt.a bin/universal/release/`

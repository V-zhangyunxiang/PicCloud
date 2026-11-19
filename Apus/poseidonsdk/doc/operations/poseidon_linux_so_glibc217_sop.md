# Poseidon Linux so (glibc 2.17) Standard Operation Procedure

## Scope
- Target artifact: `libPoseidonCypt.so`
- Target runtime baseline: `glibc <= 2.17`
- No business source code changes (`Java/Kotlin/Rust logic` unchanged)

## Prerequisites
- Docker daemon is running
- Build host has access to:
  - `/Users/zhougejie/2026_ads_projects/poseidon-lumina-2026/poseidonsdk`

## Build Environment Hardening (Step 1)
Use pinned builder based on `manylinux2014` (glibc 2.17):

- Dockerfile:
  - `script/docker/poseidon-cryptor-linux-glibc217.Dockerfile`
- Builder image tag:
  - `poseidon-cryptor-linux-glibc217-builder:latest`

Build command:

```bash
docker build -t poseidon-cryptor-linux-glibc217-builder:latest -f /Users/zhougejie/2026_ads_projects/poseidon-lumina-2026/poseidonsdk/script/docker/poseidon-cryptor-linux-glibc217.Dockerfile /Users/zhougejie/2026_ads_projects/poseidon-lumina-2026/poseidonsdk
```

## Standard Build + Verify + Publish Flow
Run one command:

```bash
/Users/zhougejie/2026_ads_projects/poseidon-lumina-2026/poseidonsdk/script/build_poseidon_linux_so_glibc217.sh
```

This script performs:
1. Build hardened builder image
2. Build Linux `so` in container
3. Copy output to `poseidon-cryptor/src/main/resources/lib/libPoseidonCypt.so`
4. Run ABI gate check:
   - GLIBC ceiling (`<=2.17`)
   - Required JNI symbols:
     - `Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_encrypt`
     - `Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_decrypt`
     - `Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_isPassableByFlowControl`

## Manual Verification Commands
Print GLIBC symbols:

```bash
strings /Users/zhougejie/2026_ads_projects/poseidon-lumina-2026/poseidonsdk/poseidon-cryptor/src/main/resources/lib/libPoseidonCypt.so | grep -Eo 'GLIBC_[0-9]+\.[0-9]+' | sort -Vu
```

Print JNI symbols:

```bash
strings /Users/zhougejie/2026_ads_projects/poseidon-lumina-2026/poseidonsdk/poseidon-cryptor/src/main/resources/lib/libPoseidonCypt.so | grep 'Java_com_poseidon_ad_main_strategy_cryptor_Cryptor_'
```

## Runtime Validation on Linux Test Server
Confirm server glibc:

```bash
getconf GNU_LIBC_VERSION
```

Smoke test the Java loader path and ensure no:
- `GLIBC_2.34 not found`
- `NoClassDefFoundError: Could not initialize class poseidon.crypt.Cryptor`


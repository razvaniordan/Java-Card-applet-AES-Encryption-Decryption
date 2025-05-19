# 🔐 Java Card Applet – AES Encryption & Decryption

This project is a Java Card applet named **`ProjectAES`** that securely encrypts and decrypts files (e.g., PDFs, DOCs) using **AES-128 ECB encryption**. It is designed to run inside the **Java Card Development Kit Simulator** provided by Oracle.

---

## ✨ Features

- Verifies user PIN before encryption/decryption
- Encrypts and decrypts binary file contents block-by-block (16 bytes)
- Uses **AES-128-bit** with ECB mode, no padding on card side (padding handled host-side using PKCS#7-style)
- Communicates via **APDU commands** from a Java host application
- Host-side encryption/decryption workflow for testing on Oracle simulator

---

## 📁 Project Structure
```bash
ProjectAES/
├── applet/
│   ├── src/                # JavaCard applet source
│   ├── bin/                # Compiled .class files
│   ├── configurations/     # .conf file with AID and output info
│   └── deliverables/       # Generated .cap, .exp, .jca files
├── client/
│   ├── src/                # Java host-side application
│   └── bin/                # Compiled host classes
├── input.pdf               # Example input file (must be added manually)
├── build.sh                # Build script that accesses the root build script of the java simulator kit
├── run.sh                  # Run script that accesses the root run script of the java simulator kit
├── README.md               # This file
```

---

## 📦 Requirements

| Tool / Component | Version | Source |
|------------------|---------|--------|
| JDK              | 17+     | [Oracle](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) |
| Java Card Development Kit Simulator | 24.1 | [Oracle Download](https://www.oracle.com/java/technologies/javacard-downloads.html#sdk-sim) |
| Java Card Development Kit Tools | 24.1 | [Oracle Download](https://www.oracle.com/java/technologies/javacard-downloads.html#sdk-tools) |
| Linux OS / WSL / Cloud Shell | Recommended | Tested on Google Cloud Shell |

## 🚀 Running the Project with Java Card Simulator

This application requires **two terminals**: one for running the **simulator** and one for the **Java host client**.

---

Make sure the following environment variables are **correctly set** in each terminal:

```bash
export JAVA_HOME=/path/to/jdk-17
export JC_HOME_SIMULATOR=/path/to/jcsdk-simulator-24.1
export JC_HOME_TOOLS=/path/to/jcsdk-tools-24.1
```

Example of my setup:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64/
export PATH=.:$JAVA_HOME/bin:$PATH
export JC_HOME_TOOLS=~/javacard/jctools
export JC_HOME_SIMULATOR=~/javacard/jcsimu
export LD_LIBRARY_PATH=$JC_HOME_SIMULATOR/runtime/bin:$JC_HOME_SIMULATOR/client/COMService
```

The keys used in the client.config.properties file are:

```bash
A000000151000000_scp03enc_10=1111111111111111111111111111111111111111111111111111111111111111
A000000151000000_scp03mac_10=2222222222222222222222222222222222222222222222222222222222222222
A000000151000000_scp03dek_10=3333333333333333333333333333333333333333333333333333333333333333
```

### 🖥️ Terminal 1 — Start the Java Card Simulator (Smart Card Server)

This step simulates the actual smart card that hosts the AES applet.

```bash
# [One-time setup only - to enable 32-bit support]
sudo dpkg --add-architecture i386
sudo apt update
sudo apt install libc6-i386

# Go to simulator root
cd $JC_HOME_SIMULATOR

# [One-time only] Run configuration tool to set up SCP keys and global PIN
java -cp . -jar tools/Configurator.jar \
  -binary runtime/bin/jcsl \
  -SCP-keyset 10 \
    1111111111111111111111111111111111111111111111111111111111111111 \
    2222222222222222222222222222222222222222222222222222222222222222 \
    3333333333333333333333333333333333333333333333333333333333333333 \
  -global-pin 01020304050f 03

# Run the simulator
./runtime/bin/jcsl
```

Leave this terminal open while interacting from the host application


### 🖥️ Terminal 2 — Run the Java Host (Off-card Client)

This step deploys the applet, communicates with it, and performs file encryption/decryption.

```bash
# Move to the sample project root
cd $JC_HOME_SIMULATOR/samples/ProjectAES

# Build applet + host, convert CAP, and verify
./build.sh

# Upload or place your input file (e.g., input.pdf) in the project root if not already added
# In Google Cloud Shell you can use the upload button or `scp` from your machine

# Run the application
./run.sh
```

## 🔐 What Happens

- Installs the Java Card applet with a fixed PIN (`01 02 03 04 05`)
- Selects and verifies the applet
- Encrypts the file in 16-byte blocks via APDUs sent to the card
- Writes the encrypted output as `encrypted_output.pdf`
- Decrypts the encrypted output and writes it as `decrypted_output.pdf`

---

## 🧪 Supported File Types

The applet operates on raw bytes, meaning it can handle **any binary file format** as long as the data is transmitted in 16-byte blocks:

- ✅ `.txt`, `.pdf`, `.docx`, `.png`, `.zip`, etc.
- ⚠️ Padding and unpadding are managed on the host side using **PKCS#7-style padding** (already implemented in this project)


---

## 📄 License

This project is for **educational use and demonstration purposes only**.  
Licensed under the [MIT License](LICENSE).


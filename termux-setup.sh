#!/usr/bin/env bash
# One-time Termux setup: Java + git + a working Java certificate store (Termux's own one is empty, which makes
# Gradle fail with "trustAnchors parameter must be non-empty"). Usage: bash termux-setup.sh 25   (or 21)
set -uo pipefail
J="${1:-25}"
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
STORE="$HOME/.fhasix-truststore.p12"
PASS="changeit"
die(){ echo "SETUP STOPPED: $*"; exit 1; }
pkg install -y "openjdk-$J" ca-certificates git curl unzip coreutils tar || die "pkg install failed (check internet, or run: pkg update)"
JH=$(ls -d "$PREFIX"/lib/jvm/java-$J* 2>/dev/null | head -1)
[ -n "$JH" ] && [ -x "$JH/bin/java" ] || die "Java $J not found in $PREFIX/lib/jvm"
export JAVA_HOME="$JH"; export PATH="$JH/bin:$PATH"
BUNDLE=""
for f in "$PREFIX/etc/tls/cert.pem" "$PREFIX/etc/ssl/certs/ca-certificates.crt" "$PREFIX/etc/ssl/cert.pem"; do
  [ -s "$f" ] && { BUNDLE="$f"; break; }
done
[ -n "$BUNDLE" ] || die "no CA certificate bundle found (run: pkg reinstall ca-certificates)"
W="$(mktemp -d)"
cat > "$W/TrustGen.java" <<'JAVA'
import java.io.*; import java.security.*; import java.security.cert.*; import java.util.*;
public class TrustGen {
  public static void main(String[] a) throws Exception {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(null, null);
    int n = 0;
    try (BufferedReader r = new BufferedReader(new FileReader(a[0]))) {
      StringBuilder sb = null; String line;
      while ((line = r.readLine()) != null) {
        if (line.startsWith("-----BEGIN CERTIFICATE-----")) sb = new StringBuilder();
        if (sb != null) sb.append(line).append('\n');
        if (sb != null && line.startsWith("-----END CERTIFICATE-----")) {
          try {
            java.security.cert.Certificate c = cf.generateCertificate(new ByteArrayInputStream(sb.toString().getBytes("US-ASCII")));
            ks.setCertificateEntry("ca" + (n++), c);
          } catch (Exception skip) { }
          sb = null;
        }
      }
    }
    try (OutputStream o = new FileOutputStream(a[1])) { ks.store(o, a[2].toCharArray()); }
    System.out.println("trusted certificates: " + n);
  }
}
JAVA

java "$W/TrustGen.java" "$BUNDLE" "$STORE" "$PASS" || die "could not build the certificate store"
rm -rf "$W"
mkdir -p "$HOME/.gradle"
GP="$HOME/.gradle/gradle.properties"; touch "$GP"
grep -v '^systemProp.javax.net.ssl' "$GP" > "$GP.new" || true
{ echo "systemProp.javax.net.ssl.trustStore=$STORE"
  echo "systemProp.javax.net.ssl.trustStorePassword=$PASS"
  echo "systemProp.javax.net.ssl.trustStoreType=PKCS12"; } >> "$GP.new"
mv "$GP.new" "$GP"
echo "Setup done."

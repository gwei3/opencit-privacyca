#!/bin/sh
# encrypt.sh - encrypt and sign with hmac
# USAGE:
#       encrypt.sh --infile plain.txt --outfile encrypted.txt --enc-passwd 
# NOTE: openssl password based key derivation function needs to be configured with
#       number of iterations but the openssl command line program does not implement
#       this option so only ONE iteration is used, which is not good enough.
# SEE ALSO:
#       OpenSSL support for RFC2898 / PBKDF2
#       http://www.mail-archive.com/openssl-users@openssl.org/msg54143.html
#       

parse_args() {
  if ! options=$(getopt -n encrypt.sh -l infile:,outfile:,enc-passwd:,auth-passwd: -- "$@"); then exit 1; fi
  eval set -- "$options"
  while [ $# -gt 0 ]
  do
    case $1 in
      --infile) INFILE="$2"; shift;;
      --outfile) OUTFILE="$2"; shift;;
      --enc-passwd) ENC_PASSWORDFILE="$2"; shift;;
      --auth-passwd) AUTH_PASSWORDFILE="$2"; shift;;
    esac
    shift
  done
}

parse_args $@

echo "Content-Type: encrypted/openssl; alg=\"aes-256-ofb\"; digest-alg=\"sha256\"" > $OUTFILE
echo "Date: $(date)" >> $OUTFILE
echo >> $OUTFILE
openssl enc -aes-256-ofb -in $INFILE -pass file:ENC_PASSWORDFILE -md sha256 -base64 >> $OUTFILE
echo ----- >> $OUTFILE
echo "Content-Type: application/signature.openssl; alg=\"hmac\"; digest-alg=\"sha256\"" >> $OUTFILE
echo >> $OUTFILE
openssl dgst -sha256 -hmac file:AUTH_PASSWORDFILE -hex $OUTFILE | awk '{ print $2 }' >> $OUTFILE


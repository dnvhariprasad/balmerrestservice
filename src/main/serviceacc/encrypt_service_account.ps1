param(
    [string]$PlainText
)

if (-not $PlainText) {
    $PlainText = Read-Host "Enter plaintext to encrypt"
}
$key = [System.Text.Encoding]::UTF8.GetBytes("BalmerLawrie2025")
$bytes = [System.Text.Encoding]::UTF8.GetBytes($PlainText)
$aes = [System.Security.Cryptography.Aes]::Create()
$aes.Mode = [System.Security.Cryptography.CipherMode]::ECB
$aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
$aes.Key = $key
$transform = $aes.CreateEncryptor()
$cipher = $transform.TransformFinalBlock($bytes, 0, $bytes.Length)
$outputPath = Join-Path $PSScriptRoot "encrypted_output.txt"
[Convert]::ToBase64String($cipher) | Out-File -Encoding utf8 $outputPath
Write-Host "Encryption complete. Output written to $outputPath"

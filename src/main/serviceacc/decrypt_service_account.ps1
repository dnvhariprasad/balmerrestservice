param(
    [string]$Base64Cipher
)

if (-not $Base64Cipher) {
    $Base64Cipher = Read-Host "Enter base64 cipher text to decrypt"
}
$key = [System.Text.Encoding]::UTF8.GetBytes("BalmerLawrie2025")
$bytes = [Convert]::FromBase64String($Base64Cipher)
$aes = [System.Security.Cryptography.Aes]::Create()
$aes.Mode = [System.Security.Cryptography.CipherMode]::ECB
$aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
$aes.Key = $key
$transform = $aes.CreateDecryptor()
$plain = $transform.TransformFinalBlock($bytes, 0, $bytes.Length)
$outputPath = Join-Path $PSScriptRoot "decrypt_output.txt"
[System.Text.Encoding]::UTF8.GetString($plain) | Out-File -Encoding utf8 $outputPath
Write-Host "Decryption complete. Output written to $outputPath"

# INSTRUKCIYA.docx -> INSTRUKCIYA.pdf через Word COM (xelatex/pandoc-pdf на машине нет)
$src = "D:\Azimut-Dev\INSTRUKCIYA.docx"
$dst = "D:\Azimut-Dev\INSTRUKCIYA.pdf"
$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    $doc = $word.Documents.Open($src)
    $doc.SaveAs([ref]$dst, [ref]17)  # 17 = wdFormatPDF
    $doc.Close($false)
    Write-Output "PDF saved: $dst"
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
}

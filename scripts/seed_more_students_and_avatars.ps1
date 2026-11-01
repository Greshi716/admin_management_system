# Adds more students and generates custom non-copyright avatar portrait photos
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = "Stop"

$baseUrl = "https://campusflow-ocm8.onrender.com"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

Write-Host "1. Authenticating as admin on $baseUrl..."
try { Invoke-WebRequest -Uri "$baseUrl/api/auth/me" -Method GET -WebSession $session -ErrorAction SilentlyContinue | Out-Null } catch {}
$csrfToken = ($session.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq "XSRF-TOKEN" }).Value
$headers = @{ "X-XSRF-TOKEN" = $csrfToken }

$loginBody = @{ username = "admin"; password = "Admin@12345" } | ConvertTo-Json
$me = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method POST -Headers $headers -Body $loginBody -ContentType "application/json" -WebSession $session
Write-Host "   Signed in as: $($me.name), Academic Year: $($me.currentAcademicYear)"

function Refresh-Csrf {
    $c = $session.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq "XSRF-TOKEN" }
    return @{ "X-XSRF-TOKEN" = $c.Value }
}

# Fetch standards, divisions, categories
$allStandards = Invoke-RestMethod -Uri "$baseUrl/api/standards/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
$stdMap = @{}
foreach ($s in $allStandards.rows) { $stdMap[$s.name] = $s.id }

$allDivisions = Invoke-RestMethod -Uri "$baseUrl/api/divisions/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
$divMap = @{}
foreach ($d in $allDivisions.rows) { $divMap["$($d.standardId)_$($d.name)"] = $d.id }

$allCategories = Invoke-RestMethod -Uri "$baseUrl/api/student-categories/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
$catMap = @{}
foreach ($c in $allCategories.rows) { $catMap[$c.code] = $c.id }

# Ensure Std 2 Div B and Std 3 Div A exist
if (-not $divMap["$($stdMap['Standard 2'])_B"]) {
    try {
        $d = Invoke-RestMethod -Uri "$baseUrl/api/divisions" -Method POST -Headers (Refresh-Csrf) -Body (@{ name = "B"; standardId = $stdMap["Standard 2"]; academicYearId = 1 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
        $divMap["$($stdMap['Standard 2'])_B"] = $d.id
    } catch {}
}
if (-not $divMap["$($stdMap['Standard 3'])_A"]) {
    try {
        $d = Invoke-RestMethod -Uri "$baseUrl/api/divisions" -Method POST -Headers (Refresh-Csrf) -Body (@{ name = "A"; standardId = $stdMap["Standard 3"]; academicYearId = 1 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
        $divMap["$($stdMap['Standard 3'])_A"] = $d.id
    } catch {}
}

Write-Host "`n2. Registering additional students..."
$newStudents = @(
    @{
        grNo = "GR-1003"; surname = "Mehta"; firstName = "Rohan"; middleName = "Nilesh"; gender = "Male";
        dob = "2018-03-10"; birthPlace = "Rajkot"; fatherName = "Nilesh Mehta"; motherName = "Pooja Mehta";
        mobile1 = "9426123456"; address = "78, Ring Road, Porbandar";
        aadhaar = "456789012345"; religionCaste = "Jain - Vania"; categoryId = $catMap["CEW"];
        admissionDate = "2026-06-16"; admissionStandardId = $stdMap["Standard 1"];
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_B"];
        initials = "RM"; color = [System.Drawing.Color]::FromArgb(217, 119, 6) # Amber
    },
    @{
        grNo = "GR-1004"; surname = "Joshi"; firstName = "Ananya"; middleName = "Mahesh"; gender = "Female";
        dob = "2017-11-05"; birthPlace = "Porbandar"; fatherName = "Mahesh Joshi"; motherName = "Geeta Joshi";
        mobile1 = "9824123456"; address = "Staff Quarters, Campus, Porbandar";
        aadhaar = "345678901234"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["STAFF"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 2"];
        standardId = $stdMap["Standard 2"]; divisionId = $divMap["$($stdMap['Standard 2'])_A"];
        initials = "AJ"; color = [System.Drawing.Color]::FromArgb(147, 51, 234) # Purple
    },
    @{
        grNo = "GR-1005"; surname = "Shah"; firstName = "Krish"; middleName = "Bhavin"; gender = "Male";
        dob = "2017-09-18"; birthPlace = "Ahmedabad"; fatherName = "Bhavin Shah"; motherName = "Kavita Shah";
        mobile1 = "9898123456"; address = "89, MG Road, Porbandar";
        aadhaar = "234567890123"; religionCaste = "Jain"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 2"];
        standardId = $stdMap["Standard 2"]; divisionId = $divMap["$($stdMap['Standard 2'])_B"];
        initials = "KS"; color = [System.Drawing.Color]::FromArgb(13, 148, 136) # Teal
    },
    @{
        grNo = "GR-1006"; surname = "Trivedi"; firstName = "Priya"; middleName = "Sanjay"; gender = "Female";
        dob = "2016-04-25"; birthPlace = "Porbandar"; fatherName = "Sanjay Trivedi"; motherName = "Bhavna Trivedi";
        mobile1 = "9727123456"; address = "102, Shanti Nagar, Porbandar";
        aadhaar = "123456789012"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 3"];
        standardId = $stdMap["Standard 3"]; divisionId = $divMap["$($stdMap['Standard 3'])_A"];
        initials = "PT"; color = [System.Drawing.Color]::FromArgb(225, 29, 72) # Rose
    },
    @{
        grNo = "GR-1007"; surname = "Varma"; firstName = "Harsh"; middleName = "Dinesh"; gender = "Male";
        dob = "2018-07-07"; birthPlace = "Porbandar"; fatherName = "Dinesh Varma"; motherName = "Rupa Varma";
        mobile1 = "9624123456"; address = "54, Subhash Nagar, Porbandar";
        aadhaar = "789012345678"; religionCaste = "Hindu - Rajput"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-17"; admissionStandardId = $stdMap["Standard 1"];
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_A"];
        initials = "HV"; color = [System.Drawing.Color]::FromArgb(79, 70, 229) # Indigo
    },
    @{
        grNo = "GR-1008"; surname = "Dave"; firstName = "Kavya"; middleName = "Hitesh"; gender = "Female";
        dob = "2017-12-14"; birthPlace = "Junagadh"; fatherName = "Hitesh Dave"; motherName = "Varsha Dave";
        mobile1 = "9512123456"; address = "23, Giri Road, Porbandar";
        aadhaar = "890123456789"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-18"; admissionStandardId = $stdMap["Standard 2"];
        standardId = $stdMap["Standard 2"]; divisionId = $divMap["$($stdMap['Standard 2'])_A"];
        initials = "KD"; color = [System.Drawing.Color]::FromArgb(234, 88, 12) # Orange
    },
    @{
        grNo = "GR-1009"; surname = "Desai"; firstName = "Aryan"; middleName = "Prakash"; gender = "Male";
        dob = "2016-09-30"; birthPlace = "Surat"; fatherName = "Prakash Desai"; motherName = "Nita Desai";
        mobile1 = "9825998877"; address = "67, Sardar Patel Nagar, Porbandar";
        aadhaar = "901234567890"; religionCaste = "Hindu - Patel"; categoryId = $catMap["CEW"];
        admissionDate = "2026-06-19"; admissionStandardId = $stdMap["Standard 3"];
        standardId = $stdMap["Standard 3"]; divisionId = $divMap["$($stdMap['Standard 3'])_A"];
        initials = "AD"; color = [System.Drawing.Color]::FromArgb(2, 132, 199) # Sky
    },
    @{
        grNo = "GR-1010"; surname = "Solanki"; firstName = "Riya"; middleName = "Mukesh"; gender = "Female";
        dob = "2018-02-14"; birthPlace = "Porbandar"; fatherName = "Mukesh Solanki"; motherName = "Lata Solanki";
        mobile1 = "9712334455"; address = "11, Station Plot, Porbandar";
        aadhaar = "567890123456"; religionCaste = "Hindu"; categoryId = $catMap["RTE"];
        admissionDate = "2026-06-20"; admissionStandardId = $stdMap["Standard 1"];
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_B"];
        initials = "RS"; color = [System.Drawing.Color]::FromArgb(192, 38, 211) # Fuchsia
    }
)

function Create-AvatarImage {
    param (
        [string]$text,
        [System.Drawing.Color]$bgColor,
        [string]$outputPath
    )
    $bmp = [System.Drawing.Bitmap]::new(400, 400)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    # Smooth circular profile with colored backdrop
    $bgBrush = [System.Drawing.SolidBrush]::new($bgColor)
    $g.FillRectangle($bgBrush, 0, 0, 400, 400)

    # Semi-transparent halo ring
    $haloBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(45, 255, 255, 255))
    $g.FillEllipse($haloBrush, 25, 25, 350, 350)

    # Avatar body silhouette (white/cream)
    $bodyBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(245, 245, 250))
    # Head circle
    $g.FillEllipse($bodyBrush, 130, 75, 140, 140)
    # Shoulders dome
    $g.FillPie($bodyBrush, 65, 220, 270, 260, 180, 180)

    # Student initials badge in the center
    $font = [System.Drawing.Font]::new("Segoe UI", [float]42, [System.Drawing.FontStyle]::Bold)
    $textBrush = [System.Drawing.SolidBrush]::new($bgColor)
    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $rect = [System.Drawing.RectangleF]::new(130, 105, 140, 80)
    $g.DrawString($text, $font, $textBrush, $rect, $format)

    $bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

function Upload-StudentPhoto {
    param (
        [long]$studentId,
        [string]$filePath
    )
    $photoBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $filePath))
    $boundary = [System.Guid]::NewGuid().ToString()
    $LF = "`r`n"
    $bodyLines = (
        "--$boundary",
        "Content-Disposition: form-data; name=`"file`"; filename=`"$([System.IO.Path]::GetFileName($filePath))`"",
        "Content-Type: image/png",
        "",
        [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($photoBytes),
        "--$boundary--",
        ""
    ) -join $LF

    $putHeaders = Refresh-Csrf
    $putHeaders["Content-Type"] = "multipart/form-data; boundary=$boundary"
    $photoRes = Invoke-RestMethod -Uri "$baseUrl/api/students/$studentId/photo" -Method PUT -Headers $putHeaders -Body ([System.Text.Encoding]::GetEncoding("ISO-8859-1").GetBytes($bodyLines)) -WebSession $session
    return $photoRes
}

# Upload photo for existing Student 2 (Diya Sharma)
$avatarPath = "avatar_DS.png"
Create-AvatarImage -text "DS" -bgColor ([System.Drawing.Color]::FromArgb(5, 150, 105)) -outputPath $avatarPath
Upload-StudentPhoto -studentId 2 -filePath $avatarPath | Out-Null
Remove-Item $avatarPath
Write-Host "   Uploaded custom avatar for Diya Sharma (GR-1002)"

# Register and upload photos for all new students
foreach ($st in $newStudents) {
    try {
        $regBody = @{
            grNo = $st.grNo;
            surname = $st.surname;
            firstName = $st.firstName;
            middleName = $st.middleName;
            gender = $st.gender;
            dob = $st.dob;
            birthPlace = $st.birthPlace;
            fatherName = $st.fatherName;
            motherName = $st.motherName;
            mobile1 = $st.mobile1;
            address = $st.address;
            aadhaar = $st.aadhaar;
            religionCaste = $st.religionCaste;
            categoryId = $st.categoryId;
            admissionDate = $st.admissionDate;
            admissionStandardId = $st.admissionStandardId;
            standardId = $st.standardId;
            divisionId = $st.divisionId;
        } | ConvertTo-Json
        $res = Invoke-RestMethod -Uri "$baseUrl/api/students" -Method POST -Headers (Refresh-Csrf) -Body $regBody -ContentType "application/json" -WebSession $session
        Write-Host "   Registered student: $($res.firstName) $($res.surname) (GR: $($res.grNo), ID: $($res.id))"

        # Generate & upload avatar
        $imgName = "avatar_$($st.grNo).png"
        Create-AvatarImage -text $st.initials -bgColor $st.color -outputPath $imgName
        $pRes = Upload-StudentPhoto -studentId $res.id -filePath $imgName
        Remove-Item $imgName
        Write-Host "      Photo uploaded (v: $($pRes.photoVersion))"
    } catch {
        Write-Host "   Note for $($st.grNo): $_"
    }
}

Write-Host "`n=== ALL STUDENTS AND CUSTOM NON-COPYRIGHT AVATARS LOADED ==="

# Comprehensive Seed script for CampusFlow via UI REST endpoints with session authentication and CSRF
$ErrorActionPreference = "Stop"

$baseUrl = "http://localhost:4200"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

Write-Host "1. Authenticating as admin..."
try {
    Invoke-WebRequest -Uri "$baseUrl/api/auth/me" -Method GET -WebSession $session -ErrorAction SilentlyContinue | Out-Null
} catch {}

$csrfCookie = $session.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq "XSRF-TOKEN" }
$csrfToken = $csrfCookie.Value
$headers = @{ "X-XSRF-TOKEN" = $csrfToken }

$loginBody = @{ username = "admin"; password = "Admin@12345" } | ConvertTo-Json
$me = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method POST -Headers $headers -Body $loginBody -ContentType "application/json" -WebSession $session
Write-Host "   Signed in as: $($me.name) ($($me.username)), Current Year: $($me.currentAcademicYear)"

function Refresh-Csrf {
    $c = $session.Cookies.GetCookies((New-Object System.Uri($baseUrl))) | Where-Object { $_.Name -eq "XSRF-TOKEN" }
    return @{ "X-XSRF-TOKEN" = $c.Value }
}

Write-Host "`n2. Seeding Standards..."
$standards = @(
    @{ name = "Standard 2"; displayOrder = 2; stream = $null; usesStream = $false },
    @{ name = "Standard 3"; displayOrder = 3; stream = $null; usesStream = $false },
    @{ name = "Standard 10"; displayOrder = 10; stream = $null; usesStream = $false },
    @{ name = "Standard 11"; displayOrder = 11; stream = "Commerce"; usesStream = $true },
    @{ name = "Standard 12"; displayOrder = 12; stream = "Science"; usesStream = $true }
)

foreach ($std in $standards) {
    try {
        $res = Invoke-RestMethod -Uri "$baseUrl/api/standards" -Method POST -Headers (Refresh-Csrf) -Body ($std | ConvertTo-Json) -ContentType "application/json" -WebSession $session
        Write-Host "   Created Standard: $($res.name) (id: $($res.id))"
    } catch {
        # Already created or note
    }
}

$allStandards = Invoke-RestMethod -Uri "$baseUrl/api/standards/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
$stdMap = @{}
foreach ($item in $allStandards.rows) {
    $stdMap[$item.name] = $item.id
    Write-Host "   Standard in map: $($item.name) -> $($item.id)"
}

Write-Host "`n3. Seeding Divisions..."
$divisions = @(
    @{ name = "A"; standardId = $stdMap["Standard 1"]; academicYearId = 1 },
    @{ name = "B"; standardId = $stdMap["Standard 1"]; academicYearId = 1 },
    @{ name = "A"; standardId = $stdMap["Standard 2"]; academicYearId = 1 },
    @{ name = "B"; standardId = $stdMap["Standard 2"]; academicYearId = 1 },
    @{ name = "A"; standardId = $stdMap["Standard 3"]; academicYearId = 1 },
    @{ name = "A"; standardId = $stdMap["Standard 10"]; academicYearId = 1 },
    @{ name = "A"; standardId = $stdMap["Standard 11"]; academicYearId = 1 },
    @{ name = "A"; standardId = $stdMap["Standard 12"]; academicYearId = 1 }
)

foreach ($div in $divisions) {
    try {
        $res = Invoke-RestMethod -Uri "$baseUrl/api/divisions" -Method POST -Headers (Refresh-Csrf) -Body ($div | ConvertTo-Json) -ContentType "application/json" -WebSession $session
        Write-Host "   Created Division: Standard $($div.standardId) Div $($res.name) (id: $($res.id))"
    } catch {
        # Already created
    }
}

$allDivisions = Invoke-RestMethod -Uri "$baseUrl/api/divisions/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
$divMap = @{}
foreach ($item in $allDivisions.rows) {
    $divMap["$($item.standardId)_$($item.name)"] = $item.id
    Write-Host "   Division in map: Std $($item.standardId) Div $($item.name) -> $($item.id)"
}

Write-Host "`n4. Seeding Fee Structure & Finalising..."
$categories = Invoke-RestMethod -Uri "$baseUrl/api/student-categories/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
$catMap = @{}
foreach ($c in $categories.rows) {
    $catMap[$c.code] = $c.id
}

$struct = Invoke-RestMethod -Uri "$baseUrl/api/fee-structures/1" -Method GET -WebSession $session
Write-Host "   Fee structure current status: $($struct.status), rows count: $($struct.rows.Count)"

if ($struct.status -ne "FINALISED") {
    $feeRows = @(
        @{ standardId = $stdMap["Standard 1"]; categoryId = $catMap["O/S"]; amount = 15000; registrationFee = 500; dueDate = "2026-11-30" },
        @{ standardId = $stdMap["Standard 1"]; categoryId = $catMap["CEW"]; amount = 12000; registrationFee = 500; dueDate = "2026-11-30" },
        @{ standardId = $stdMap["Standard 1"]; categoryId = $catMap["RTE"]; amount = 0; registrationFee = 0; dueDate = "2026-11-30" },
        @{ standardId = $stdMap["Standard 2"]; categoryId = $catMap["O/S"]; amount = 18000; registrationFee = 500; dueDate = "2026-11-30" },
        @{ standardId = $stdMap["Standard 2"]; categoryId = $catMap["STAFF"]; amount = 9000; registrationFee = 250; dueDate = "2026-11-30" },
        @{ standardId = $stdMap["Standard 3"]; categoryId = $catMap["O/S"]; amount = 20000; registrationFee = 500; dueDate = "2026-11-30" }
    )

    foreach ($row in $feeRows) {
        try {
            $rowBody = @{
                version = $null;
                structureVersion = $struct.version;
                standardId = $row.standardId;
                categoryId = $row.categoryId;
                amount = $row.amount;
                registrationFee = $row.registrationFee;
                dueDate = $row.dueDate
            } | ConvertTo-Json
            $res = Invoke-RestMethod -Uri "$baseUrl/api/fee-structures/1/rows" -Method POST -Headers (Refresh-Csrf) -Body $rowBody -ContentType "application/json" -WebSession $session
            Write-Host "   Added Fee Row: Std $($row.standardId), Cat $($row.categoryId), Amount Rs $($row.amount)"
        } catch {
            Write-Host "   Fee row note: $_"
        }
    }

    try {
        $structLatest = Invoke-RestMethod -Uri "$baseUrl/api/fee-structures/1" -Method GET -WebSession $session
        $finaliseBody = @{ version = $structLatest.version } | ConvertTo-Json
        $fin = Invoke-RestMethod -Uri "$baseUrl/api/fee-structures/1/finalise" -Method POST -Headers (Refresh-Csrf) -Body $finaliseBody -ContentType "application/json" -WebSession $session
        Write-Host "   Fee structure for Year 1 finalised! Status: $($fin.status)"
    } catch {
        Write-Host "   Finalise note: $_"
    }
} else {
    Write-Host "   Fee structure is already FINALISED."
}

Write-Host "`n5. Registering Students..."
$studentsData = @(
    @{
        grNo = "GR-1001"; surname = "Patel"; firstName = "Aarav"; middleName = "Rajesh"; gender = "Male";
        dob = "2018-05-12"; birthPlace = "Porbandar"; fatherName = "Rajesh Patel"; motherName = "Kiran Patel";
        mobile1 = "9825123456"; mobile2 = "9825654321"; address = "12, Patel Colony, Porbandar";
        aadhaar = "897456123412"; religionCaste = "Hindu - Patel"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 1"]; previousSchool = "Kindergarten Academy";
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_A"]
    },
    @{
        grNo = "GR-1002"; surname = "Sharma"; firstName = "Diya"; middleName = "Vikram"; gender = "Female";
        dob = "2018-08-22"; birthPlace = "Porbandar"; fatherName = "Vikram Sharma"; motherName = "Sunita Sharma";
        mobile1 = "9879123456"; mobile2 = $null; address = "45, Temple Road, Porbandar";
        aadhaar = "678912345678"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["RTE"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 1"]; previousSchool = "Little Flowers Pre-school";
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_A"]
    },
    @{
        grNo = "GR-1003"; surname = "Mehta"; firstName = "Rohan"; middleName = "Nilesh"; gender = "Male";
        dob = "2018-03-10"; birthPlace = "Rajkot"; fatherName = "Nilesh Mehta"; motherName = "Pooja Mehta";
        mobile1 = "9426123456"; mobile2 = "9426987654"; address = "78, Ring Road, Porbandar";
        aadhaar = "456789012345"; religionCaste = "Jain - Vania"; categoryId = $catMap["CEW"];
        admissionDate = "2026-06-16"; admissionStandardId = $stdMap["Standard 1"]; previousSchool = "Bright Kids Nursery";
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_B"]
    },
    @{
        grNo = "GR-1004"; surname = "Joshi"; firstName = "Ananya"; middleName = "Mahesh"; gender = "Female";
        dob = "2017-11-05"; birthPlace = "Porbandar"; fatherName = "Mahesh Joshi"; motherName = "Geeta Joshi";
        mobile1 = "9824123456"; mobile2 = $null; address = "Staff Quarters, Campus, Porbandar";
        aadhaar = "345678901234"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["STAFF"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 2"]; previousSchool = "Primary School 1";
        standardId = $stdMap["Standard 2"]; divisionId = $divMap["$($stdMap['Standard 2'])_A"]
    },
    @{
        grNo = "GR-1005"; surname = "Shah"; firstName = "Krish"; middleName = "Bhavin"; gender = "Male";
        dob = "2017-09-18"; birthPlace = "Ahmedabad"; fatherName = "Bhavin Shah"; motherName = "Kavita Shah";
        mobile1 = "9898123456"; mobile2 = "9898654321"; address = "89, MG Road, Porbandar";
        aadhaar = "234567890123"; religionCaste = "Jain"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 2"]; previousSchool = "Saint Mary School";
        standardId = $stdMap["Standard 2"]; divisionId = $divMap["$($stdMap['Standard 2'])_B"]
    },
    @{
        grNo = "GR-1006"; surname = "Trivedi"; firstName = "Priya"; middleName = "Sanjay"; gender = "Female";
        dob = "2016-04-25"; birthPlace = "Porbandar"; fatherName = "Sanjay Trivedi"; motherName = "Bhavna Trivedi";
        mobile1 = "9727123456"; mobile2 = $null; address = "102, Shanti Nagar, Porbandar";
        aadhaar = "123456789012"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-15"; admissionStandardId = $stdMap["Standard 3"]; previousSchool = "City Model School";
        standardId = $stdMap["Standard 3"]; divisionId = $divMap["$($stdMap['Standard 3'])_A"]
    },
    @{
        grNo = "GR-1007"; surname = "Varma"; firstName = "Harsh"; middleName = "Dinesh"; gender = "Male";
        dob = "2018-07-07"; birthPlace = "Porbandar"; fatherName = "Dinesh Varma"; motherName = "Rupa Varma";
        mobile1 = "9624123456"; mobile2 = $null; address = "54, Subhash Nagar, Porbandar";
        aadhaar = "789012345678"; religionCaste = "Hindu - Rajput"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-17"; admissionStandardId = $stdMap["Standard 1"]; previousSchool = "Playway Nursery";
        standardId = $stdMap["Standard 1"]; divisionId = $divMap["$($stdMap['Standard 1'])_A"]
    },
    @{
        grNo = "GR-1008"; surname = "Dave"; firstName = "Kavya"; middleName = "Hitesh"; gender = "Female";
        dob = "2017-12-14"; birthPlace = "Junagadh"; fatherName = "Hitesh Dave"; motherName = "Varsha Dave";
        mobile1 = "9512123456"; mobile2 = "9512654321"; address = "23, Giri Road, Porbandar";
        aadhaar = "890123456789"; religionCaste = "Hindu - Brahmin"; categoryId = $catMap["O/S"];
        admissionDate = "2026-06-18"; admissionStandardId = $stdMap["Standard 2"]; previousSchool = "Junagadh Public School";
        standardId = $stdMap["Standard 2"]; divisionId = $divMap["$($stdMap['Standard 2'])_A"]
    }
)

$createdStudents = @{}
foreach ($s in $studentsData) {
    try {
        $res = Invoke-RestMethod -Uri "$baseUrl/api/students" -Method POST -Headers (Refresh-Csrf) -Body ($s | ConvertTo-Json) -ContentType "application/json" -WebSession $session
        Write-Host "   Registered student: $($res.firstName) $($res.surname) (GR: $($res.grNo), ID: $($res.id))"
        $createdStudents[$res.grNo] = $res
    } catch {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "   Student $($s.grNo) error: $($reader.ReadToEnd())"
    }
}

# Fetch student IDs if already registered
if ($createdStudents.Count -eq 0) {
    $stdList = Invoke-RestMethod -Uri "$baseUrl/api/students/search" -Method POST -Headers (Refresh-Csrf) -Body (@{ first = 0; rows = 50 } | ConvertTo-Json) -ContentType "application/json" -WebSession $session
    foreach ($item in $stdList.rows) {
        $createdStudents[$item.grNo] = $item
    }
}

Write-Host "`n6. Uploading Student Photo for GR-1001..."
$sampleImg = "frontend/node_modules/playwright-core/lib/server/chromium/appIcon.png"
if (Test-Path $sampleImg) {
    $s1 = $createdStudents["GR-1001"]
    if ($s1) {
        try {
            $photoBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $sampleImg))
            $boundary = [System.Guid]::NewGuid().ToString()
            $LF = "`r`n"
            $bodyLines = (
                "--$boundary",
                "Content-Disposition: form-data; name=`"file`"; filename=`"photo.png`"",
                "Content-Type: image/png",
                "",
                [System.Text.Encoding]::GetEncoding("ISO-8859-1").GetString($photoBytes),
                "--$boundary--",
                ""
            ) -join $LF
            $putHeaders = Refresh-Csrf
            $putHeaders["Content-Type"] = "multipart/form-data; boundary=$boundary"
            $photoRes = Invoke-RestMethod -Uri "$baseUrl/api/students/$($s1.id)/photo" -Method PUT -Headers $putHeaders -Body ([System.Text.Encoding]::GetEncoding("ISO-8859-1").GetBytes($bodyLines)) -WebSession $session
            Write-Host "   Photo uploaded for student $($s1.id), photoVersion: $($photoRes.photoVersion)"
        } catch {
            Write-Host "   Photo upload note: $_"
        }
    }
}

Write-Host "`n7. Updating Student Profile (Audit demonstration)..."
$sDave = $createdStudents["GR-1008"]
if ($sDave) {
    try {
        $profile = Invoke-RestMethod -Uri "$baseUrl/api/students/$($sDave.id)" -Method GET -WebSession $session
        $editBody = @{
            version = $profile.version;
            grNo = $profile.grNo;
            surname = $profile.surname;
            firstName = $profile.firstName;
            middleName = $profile.middleName;
            gender = $profile.gender;
            dob = $profile.dob;
            birthPlace = $profile.birthPlace;
            fatherName = $profile.fatherName;
            motherName = $profile.motherName;
            mobile1 = "9512999999";
            mobile2 = $profile.mobile2;
            address = "Flat 402, Sea View Apartments, Porbandar";
            aadhaar = $profile.aadhaar;
            religionCaste = $profile.religionCaste;
            categoryId = $profile.categoryId;
            admissionDate = $profile.admissionDate;
            admissionStandardId = $profile.admissionStandardId;
            previousSchool = $profile.previousSchool
        } | ConvertTo-Json
        $updated = Invoke-RestMethod -Uri "$baseUrl/api/students/$($sDave.id)" -Method PUT -Headers (Refresh-Csrf) -Body $editBody -ContentType "application/json" -WebSession $session
        Write-Host "   Updated Kavya Dave profile (address and mobile updated for audit log)"
    } catch {
        Write-Host "   Edit student note: $_"
    }
}

Write-Host "`n8. Adding Brought Forward Balance for Rohan Mehta (GR-1003)..."
$sMehta = $createdStudents["GR-1003"]
if ($sMehta) {
    try {
        $bfBody = @{ version = $null; amount = 2500; note = "Pending fees from academic year 2025-26" } | ConvertTo-Json
        $bf = Invoke-RestMethod -Uri "$baseUrl/api/students/$($sMehta.id)/brought-forward" -Method PUT -Headers (Refresh-Csrf) -Body $bfBody -ContentType "application/json" -WebSession $session
        Write-Host "   Brought forward fee balance added: Rs $($bf.amount)"
    } catch {
        Write-Host "   Brought forward note: $_"
    }
}

Write-Host "`n9. Collecting Fees & Issuing Receipts..."
# Aarav Patel - Cash payment
$s1 = $createdStudents["GR-1001"]
if ($s1) {
    try {
        $feeContext = Invoke-RestMethod -Uri "$baseUrl/api/students/$($s1.id)/fee-collection" -Method GET -WebSession $session
        $receipt1 = @{
            studentId = $s1.id;
            receiptType = "CURRENT_YEAR";
            receiptNo = "REC-2026-001";
            receiptDate = "2026-06-20";
            registrationPaid = 500;
            feePaid = 7500;
            previousYearId = $null;
            paymentMode = "CASH";
            chequeNo = $null;
            chequeDate = $null;
            bankName = $null;
            balanceVersion = $feeContext.balanceVersion
        } | ConvertTo-Json
        $r1 = Invoke-RestMethod -Uri "$baseUrl/api/receipts" -Method POST -Headers (Refresh-Csrf) -Body $receipt1 -ContentType "application/json" -WebSession $session
        Write-Host "   Issued Cash Receipt: $($r1.receiptNo) for $($r1.studentName), Total: Rs $($r1.total)"
    } catch {
        Write-Host "   Receipt 1 note: $_"
    }
}

# Ananya Joshi - Cheque payment
$s4 = $createdStudents["GR-1004"]
if ($s4) {
    try {
        $feeContext = Invoke-RestMethod -Uri "$baseUrl/api/students/$($s4.id)/fee-collection" -Method GET -WebSession $session
        $receipt2 = @{
            studentId = $s4.id;
            receiptType = "CURRENT_YEAR";
            receiptNo = "REC-2026-002";
            receiptDate = "2026-06-22";
            registrationPaid = 250;
            feePaid = 4500;
            previousYearId = $null;
            paymentMode = "CHEQUE";
            chequeNo = "451201";
            chequeDate = "2026-06-21";
            bankName = "State Bank of India";
            balanceVersion = $feeContext.balanceVersion
        } | ConvertTo-Json
        $r2 = Invoke-RestMethod -Uri "$baseUrl/api/receipts" -Method POST -Headers (Refresh-Csrf) -Body $receipt2 -ContentType "application/json" -WebSession $session
        Write-Host "   Issued Cheque Receipt: $($r2.receiptNo) for $($r2.studentName), Total: Rs $($r2.total)"
    } catch {
        Write-Host "   Receipt 2 note: $_"
    }
}

# Krish Shah - Cheque payment that bounces
$s5 = $createdStudents["GR-1005"]
if ($s5) {
    try {
        $feeContext = Invoke-RestMethod -Uri "$baseUrl/api/students/$($s5.id)/fee-collection" -Method GET -WebSession $session
        $receipt3 = @{
            studentId = $s5.id;
            receiptType = "CURRENT_YEAR";
            receiptNo = "REC-2026-003";
            receiptDate = "2026-06-25";
            registrationPaid = 500;
            feePaid = 5000;
            previousYearId = $null;
            paymentMode = "CHEQUE";
            chequeNo = "998811";
            chequeDate = "2026-06-24";
            bankName = "HDFC Bank";
            balanceVersion = $feeContext.balanceVersion
        } | ConvertTo-Json
        $r3 = Invoke-RestMethod -Uri "$baseUrl/api/receipts" -Method POST -Headers (Refresh-Csrf) -Body $receipt3 -ContentType "application/json" -WebSession $session
        Write-Host "   Issued Cheque Receipt: $($r3.receiptNo), SerialNo: $($r3.serialNo)"

        # Mark receipt 3 as NOT CLEARED (bounced cheque audit!)
        $feeContextAfter = Invoke-RestMethod -Uri "$baseUrl/api/students/$($s5.id)/fee-collection" -Method GET -WebSession $session
        $bounceBody = @{
            reason = "Cheque bounced due to signature mismatch and insufficient funds";
            balanceVersion = $feeContextAfter.balanceVersion
        } | ConvertTo-Json
        $bounced = Invoke-RestMethod -Uri "$baseUrl/api/receipts/$($r3.serialNo)/not-cleared" -Method POST -Headers (Refresh-Csrf) -Body $bounceBody -ContentType "application/json" -WebSession $session
        Write-Host "   Marked Receipt $($r3.serialNo) as NOT CLEARED (Bounced) - Audit recorded!"
    } catch {
        Write-Host "   Receipt 3 note: $_"
    }
}

Write-Host "`n10. Google Form Admissions Upload & Review..."
$admissionsCsv = "management/src/test/resources/admissions/responses-sample.csv"
if (Test-Path $admissionsCsv) {
    try {
        $csvBytes = [System.IO.File]::ReadAllBytes((Resolve-Path $admissionsCsv))
        $boundary = [System.Guid]::NewGuid().ToString()
        $LF = "`r`n"
        $bodyLines = (
            "--$boundary",
            "Content-Disposition: form-data; name=`"file`"; filename=`"responses-sample.csv`"",
            "Content-Type: text/csv",
            "",
            [System.Text.Encoding]::UTF8.GetString($csvBytes),
            "--$boundary--",
            ""
        ) -join $LF
        $upHeaders = Refresh-Csrf
        $upHeaders["Content-Type"] = "multipart/form-data; boundary=$boundary"
        $impResult = Invoke-RestMethod -Uri "$baseUrl/api/admissions/upload" -Method POST -Headers $upHeaders -Body ([System.Text.Encoding]::UTF8.GetBytes($bodyLines)) -WebSession $session
        Write-Host "   Uploaded Google Form responses! Total: $($impResult.totalRows), Imported: $($impResult.importedCount), Duplicates: $($impResult.duplicateCount)"

        # Fetch pending admissions
        $pendingList = Invoke-RestMethod -Uri "$baseUrl/api/admissions?status=PENDING" -Method GET -WebSession $session
        if ($pendingList.rows -and $pendingList.rows.Count -gt 0) {
            # Approve the first pending admission
            $toApprove = $pendingList.rows[0]
            $approveDetail = Invoke-RestMethod -Uri "$baseUrl/api/admissions/$($toApprove.id)" -Method GET -WebSession $session
            $approveBody = @{
                grNo = "GR-1020";
                academicYearId = 1;
                divisionId = $divMap["$($stdMap['Standard 1'])_A"];
                categoryId = $catMap["O/S"];
                admissionDate = "2026-06-25";
                form = $approveDetail.form;
                version = $approveDetail.version
            } | ConvertTo-Json
            $appResult = Invoke-RestMethod -Uri "$baseUrl/api/admissions/$($toApprove.id)/approve" -Method POST -Headers (Refresh-Csrf) -Body $approveBody -ContentType "application/json" -WebSession $session
            Write-Host "   Approved Admission Request id $($toApprove.id) for $($toApprove.name) -> New Student created!"

            # Reject the second pending admission if exists
            if ($pendingList.rows.Count -gt 1) {
                $toReject = $pendingList.rows[1]
                $rejectDetail = Invoke-RestMethod -Uri "$baseUrl/api/admissions/$($toReject.id)" -Method GET -WebSession $session
                $rejectBody = @{
                    remarks = "Incomplete parent documentation and birth certificate missing";
                    version = $rejectDetail.version
                } | ConvertTo-Json
                $rejResult = Invoke-RestMethod -Uri "$baseUrl/api/admissions/$($toReject.id)/reject" -Method POST -Headers (Refresh-Csrf) -Body $rejectBody -ContentType "application/json" -WebSession $session
                Write-Host "   Rejected Admission Request id $($toReject.id) with remark: Incomplete parent documentation"
            }
        }
    } catch {
        Write-Host "   Admissions import note: $_"
    }
}

Write-Host "`n11. Creating Next Academic Year (2027-28)..."
try {
    # 2026-27 is 2026-10-25 to 2027-10-25. So 2027-28 starts 2027-10-26 to 2028-10-25!
    $nextYear = @{
        name = "2027-28";
        startDate = "2027-10-26";
        endDate = "2028-10-25";
        copyFromLast = $true
    } | ConvertTo-Json
    $y2 = Invoke-RestMethod -Uri "$baseUrl/api/academic-years" -Method POST -Headers (Refresh-Csrf) -Body $nextYear -ContentType "application/json" -WebSession $session
    Write-Host "   Created Next Academic Year: $($y2.name) (id: $($y2.id))"
} catch {
    Write-Host "   Next year note: $_"
}

Write-Host "`n=== SEEDING COMPLETED SUCCESSFULLY ==="

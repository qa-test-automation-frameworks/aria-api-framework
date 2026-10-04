            # Local Vulnerability Scan

            SBOM: `build/reports/cyclonedx/bom.json`

            Command: `/tmp/portfolio-tools/bin/osv-scanner scan source --sbom /home/vyaspc/Documents/Repo/aria-api-framework/build/reports/cyclonedx/bom.json`

            Exit code: `0`

            ```text
            Warning: --sbom has been deprecated in favor of -L
Starting filesystem walk for root: /
Scanned /home/vyaspc/Documents/Repo/aria-api-framework/build/reports/cyclonedx/bom.json file and found 295 packages
End status: 0 dirs visited, 1 inodes visited, 1 Extract calls, 29.854669ms elapsed, 29.854764ms wall time

No issues found
            ```
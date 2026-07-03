package com.edu.imageconversion.download;

import com.edu.imageconversion.download.dto.DownloadRequest;
import com.edu.imageconversion.download.dto.DownloadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/downloads")
public class DownloadController {

    private static final Logger logger = LogManager.getLogger(DownloadController.class);

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @Operation(
            summary = "Download files in parallel",
            description = "Accepts a list of URLs, downloads them concurrently via a thread pool, " +
                    "and saves each file to the target directory. Individual failures are reported " +
                    "per-URL (status=failed) and do not affect the overall HTTP response."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Result list for all requested URLs"),
            @ApiResponse(responseCode = "400", description = "Validation error — empty URL list", content = @Content)
    })
    @PostMapping
    public ResponseEntity<List<DownloadResult>> downloadFiles(@Valid @RequestBody DownloadRequest request) {
        logger.info("Download request received: {} URL(s), targetDir='{}'",
                request.urls().size(), request.targetDir());
        List<DownloadResult> results = downloadService.downloadAll(request.urls(), request.targetDir());
        return ResponseEntity.ok(results);
    }
}

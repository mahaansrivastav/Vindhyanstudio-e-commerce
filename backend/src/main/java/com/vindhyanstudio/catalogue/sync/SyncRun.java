/*
 * Copyright (c) 2026 Mahan Srivastava. All rights reserved.
 * Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
 */
package com.vindhyanstudio.catalogue.sync;

import java.time.OffsetDateTime;
import java.util.List;

import com.vindhyanstudio.common.Json;

import jakarta.persistence.*;

@Entity
@Table(name = "sync_runs")
public class SyncRun {

    public record Issue(String severity, int row, String sku, String message) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private OffsetDateTime startedAt = OffsetDateTime.now();
    private OffsetDateTime finishedAt;
    private int rowsOk;
    private int rowsFailed;
    private String errorsJson;

    public Long getId() { return id; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public int getRowsOk() { return rowsOk; }
    public int getRowsFailed() { return rowsFailed; }

    public List<Issue> getIssues() {
        List<Issue> l = Json.read(errorsJson, new tools.jackson.core.type.TypeReference<List<Issue>>() {});
        return l == null ? List.of() : l;
    }

    public void finish(int ok, int failed, List<Issue> issues) {
        this.finishedAt = OffsetDateTime.now();
        this.rowsOk = ok;
        this.rowsFailed = failed;
        String json = Json.write(issues);
        // Keep within the column size even if the sheet is very broken.
        this.errorsJson = json.length() > 19000 ? Json.write(issues.subList(0, Math.min(issues.size(), 100))) : json;
    }
}

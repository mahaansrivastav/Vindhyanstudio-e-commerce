package com.vindhyanstudio.catalogue.sync;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findTop10ByOrderByIdDesc();
}

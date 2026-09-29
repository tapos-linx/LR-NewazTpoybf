"""
Forensic Archive Database Models for LR-NewazTpoybf.
Compatible with SQLite (local development/offline) and PostgreSQL + PostGIS (production scale).
"""

from typing import Optional, List
import datetime
from pydantic import BaseModel, Field

# SQLite & PostGIS schema definitions
POSTGIS_DDL = """
-- PostgreSQL + PostGIS Production Schema for LR-NewazTpoybf
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    filename VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL UNIQUE,
    survey_type VARCHAR(50) NOT NULL, -- CS, SA, RS, BS, DEEDS, MUTATION, WARISH, MAPS
    district VARCHAR(100),
    upazila VARCHAR(100),
    mouza VARCHAR(100),
    jl_number VARCHAR(50),
    khatian_no VARCHAR(50),
    dag_no VARCHAR(50),
    total_area_decimal NUMERIC(10, 4),
    is_duplicate BOOLEAN DEFAULT FALSE,
    source_folder TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ocr_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    raw_text TEXT NOT NULL,
    confidence NUMERIC(5, 4) NOT NULL,
    has_unclear BOOLEAN DEFAULT FALSE,
    unclear_segments JSONB DEFAULT '[]'::jsonb,
    marginal_notes JSONB DEFAULT '[]'::jsonb,
    stamps_seals JSONB DEFAULT '[]'::jsonb,
    engine_name VARCHAR(100) DEFAULT 'PaddleOCR+Tesseract',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS hcr_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    context_type VARCHAR(50) NOT NULL,
    original_handwriting TEXT NOT NULL,
    corrected_transcription TEXT,
    confidence NUMERIC(5, 4) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    editor_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cadastral_maps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    mouza_name VARCHAR(100) NOT NULL,
    survey_type VARCHAR(50) NOT NULL,
    sheet_number VARCHAR(50) NOT NULL,
    scale VARCHAR(50) DEFAULT '16 inches = 1 mile',
    north_arrow_bearing NUMERIC(5, 2) DEFAULT 0.0,
    sheet_boundary GEOMETRY(Polygon, 4326),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cadastral_plots (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    map_id UUID REFERENCES cadastral_maps(id) ON DELETE CASCADE,
    dag_no VARCHAR(50) NOT NULL,
    dag_no_bn VARCHAR(50),
    geom GEOMETRY(Polygon, 4326),
    area_decimals NUMERIC(10, 4),
    land_class VARCHAR(50),
    is_matched_inheritance BOOLEAN DEFAULT FALSE,
    highlight_color VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS land_owners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    owner_name VARCHAR(255) NOT NULL,
    father_or_husband VARCHAR(255),
    relationship VARCHAR(100),
    hissa_decimal NUMERIC(7, 6) NOT NULL,
    hissa_anna VARCHAR(50),
    survey_type VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS evidence_audits (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    document_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    evidence_tier VARCHAR(50) NOT NULL, -- VERIFIED, CORROBORATED, PROBABLE, POSSIBLE, RECORD GAP, CONTRADICTORY
    confidence_score NUMERIC(5, 4) NOT NULL,
    discrepancies JSONB DEFAULT '[]'::jsonb,
    chain_continuity_notes TEXT,
    audited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS batch_jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_name VARCHAR(255) NOT NULL,
    total_files INT NOT NULL,
    processed_files INT DEFAULT 0,
    status VARCHAR(50) DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_docs_sha256 ON documents(sha256);
CREATE INDEX IF NOT EXISTS idx_plots_dag ON cadastral_plots(dag_no);
CREATE INDEX IF NOT EXISTS idx_evidence_tier ON evidence_audits(evidence_tier);
"""

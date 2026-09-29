from fastapi import FastAPI, UploadFile, File, Form, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional
import uvicorn
import os
from backend.api.bulk_upload import router as bulk_upload_router
from backend.api.hcr import router as hcr_router
from backend.api.gis import router as gis_router
from backend.api.matcher import router as matcher_router
from backend.api.export import router as export_router

app = FastAPI(
    title="LR-NewazTpoybf Forensic Land Record API",
    description="Forensic Bangladesh land-record research platform API for zero-data-loss document processing, Bangla OCR/HCR, and cadastral survey map intelligence.",
    version="1.0.0"
)

app.include_router(bulk_upload_router)
app.include_router(hcr_router)
app.include_router(gis_router)
app.include_router(matcher_router)
app.include_router(export_router)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": "LR-NewazTpoybf-backend",
        "version": "1.0.0",
        "modules": {
            "ocr": "Tesseract + PaddleOCR (Bangla)",
            "hcr": "TrOCR + Donut (Bangla Handwriting)",
            "gis": "OpenCV + GDAL + Rasterio (Cadastral Maps)",
            "matcher": "Inheritance-to-Map Warish Engine"
        }
    }

@app.get("/api/v1/surveys/types")
async def get_survey_types():
    return [
        {"code": "CS", "name_bn": "সি এস খতিয়ান (ক্যাডাস্ট্রাল সার্ভে)", "period": "1888-1940"},
        {"code": "SA", "name_bn": "এস এ খতিয়ান (স্টেট একুইজিশন)", "period": "1956-1962"},
        {"code": "RS", "name_bn": "আর এস খতিয়ান (রিভিশনাল সার্ভে)", "period": "1965-1990"},
        {"code": "BRS_BS", "name_bn": "বি আর এস / বি এস / সিটি জরিপ", "period": "1998-Present"},
        {"code": "MUTATION", "name_bn": "নামজারি ও জমাভাগ খতিয়ান", "period": "Ongoing"},
        {"code": "DEEDS", "name_bn": "দলিল (সাফ-কবলা/হেবা/বন্টননামা)", "period": "Historical & Modern"},
        {"code": "WARISH", "name_bn": "ওয়ারিশনামা / উত্তরাধিকার সনদ", "period": "Family Inheritance"},
        {"code": "MAPS", "name_bn": "মৌজা নকশা / সি এস / আর এস ম্যাপ", "period": "Cadastral Sheets"}
    ]

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)

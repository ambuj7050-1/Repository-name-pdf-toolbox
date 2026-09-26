# PDF Toolbox — Deployment Ready

A modern PDF utility website with a Java Spring Boot + Apache PDFBox backend and a Netlify-ready static frontend.

## Tools

- Merge PDF
- Split PDF
- Compress PDF
- Rotate PDF
- Delete Pages
- Extract Pages
- PDF to JPG
- JPG/PNG to PDF

## Local development

Requirements: Java 21+ and Maven 3.9+.

```powershell
mvn clean install
mvn spring-boot:run
```

Open `http://localhost:8080`.

## Deployment structure

```text
PDF Toolbox
├── netlify-site/       # deploy this folder to Netlify
├── src/                # Spring Boot backend
├── pom.xml
├── netlify.toml
├── DEPLOYMENT.md
└── README.md
```

Before deploying the frontend, set `window.PDF_API_BASE_URL` in `netlify-site/config.js` to the public HTTPS URL of your Java backend.

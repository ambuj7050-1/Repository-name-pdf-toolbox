# PDF Toolbox — Deployment Guide

This package is split into two deployable parts:

- `netlify-site/` — static frontend for Netlify
- Spring Boot project in the root — Java/PDFBox backend

## 1. Deploy the Java backend

Build and test locally:

```powershell
mvn clean package
java -jar target/pdf-toolbox-1.0.0.jar
```

Deploy the root Spring Boot project to a Java-compatible host. The service must expose the application on the host-provided port. The app currently uses port 8080 locally.

After deployment, copy the backend's HTTPS URL.

## 2. Connect the frontend

Open:

`netlify-site/config.js`

Change:

```js
window.PDF_API_BASE_URL = "";
```

to your backend URL, for example:

```js
window.PDF_API_BASE_URL = "https://your-backend.example.com";
```

Do not add a trailing slash.

## 3. Deploy to Netlify

The repository root contains `netlify.toml`, which tells Netlify to publish `netlify-site`.

You can connect the repository to Netlify or manually deploy the `netlify-site` folder. Netlify's documentation confirms that static site folders can be deployed by drag-and-drop and that `netlify.toml` can define the publish directory.

## 4. CORS

The current Java controller permits cross-origin browser requests. For a public production service, replace the wildcard CORS policy with your exact Netlify domain and add authentication/rate limiting as appropriate.

## 5. Production checklist

- HTTPS on the backend
- Exact CORS origin for the production frontend
- File-size and request limits
- Rate limiting
- Automatic cleanup of temporary files
- Virus/malware scanning for public uploads
- Monitoring and logs
- Authentication if accounts are added
- Backups for any persistent data

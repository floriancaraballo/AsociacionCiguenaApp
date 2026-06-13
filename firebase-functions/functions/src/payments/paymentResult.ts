import {onRequest} from "firebase-functions/v2/https";

export const paymentResult = onRequest(
  {region: "europe-west1"},
  async (req, res) => {
    const rawStatus = String(req.query.status || "error");
    const status = rawStatus === "success" ? "success" : "error";
    const title = status === "success" ?
      "Pago completado" :
      "Pago no completado";
    const message = status === "success" ?
      "Puedes volver a la aplicacion." :
      "La operacion se ha cancelado o no ha sido autorizada.";

    res
      .status(200)
      .set("Content-Type", "text/html; charset=utf-8")
      .send(`<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
</head>
<body>
  <h1>${title}</h1>
  <p>${message}</p>
</body>
</html>`);
  }
);

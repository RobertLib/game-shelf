export default function sanitizeHTML(html) {
  const doc = new DOMParser().parseFromString(html, "text/html");
  const scripts = doc.querySelectorAll("script");

  scripts.forEach((script) => script.remove());

  const elements = doc.body.querySelectorAll("*");

  elements.forEach((el) => {
    Array.from(el.attributes).forEach((attr) => {
      if (attr.name.startsWith("on")) {
        el.removeAttribute(attr.name);
      }
    });
  });

  return doc.body.innerHTML;
}

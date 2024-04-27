const locales = undefined;

export default function formatDate(date, format) {
  if (!date) {
    return "";
  }

  const parsedDate = typeof date === "string" ? new Date(date) : date;

  if (format === "date") {
    return parsedDate.toLocaleDateString(locales, {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
    });
  }

  if (format === "time") {
    return parsedDate.toLocaleTimeString(locales, {
      hour: "2-digit",
      minute: "2-digit",
    });
  }

  if (format === "YYYY-MM-DD") {
    return `${parsedDate.getFullYear()}-${String(
      parsedDate.getMonth() + 1
    ).padStart(2, "0")}-${String(parsedDate.getDate()).padStart(2, "0")}`;
  }

  if (format === "HH:MM") {
    return `${String(parsedDate.getHours()).padStart(2, "0")}:${String(
      parsedDate.getMinutes()
    ).padStart(2, "0")}`;
  }

  if (format === "YYYY-MM-DD HH:MM") {
    return `${parsedDate.getFullYear()}-${String(
      parsedDate.getMonth() + 1
    ).padStart(2, "0")}-${String(parsedDate.getDate()).padStart(
      2,
      "0"
    )} ${String(parsedDate.getHours()).padStart(2, "0")}:${String(
      parsedDate.getMinutes()
    ).padStart(2, "0")}`;
  }

  return parsedDate.toLocaleString(locales, {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

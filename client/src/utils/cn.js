export default function cn(...inputs) {
  let tmp;
  let str = "";

  for (let i = 0; i < inputs.length; i++) {
    if ((tmp = inputs[i])) {
      if (typeof tmp === "string") {
        str += (str && " ") + tmp;
      }
    }
  }

  return str || undefined;
}

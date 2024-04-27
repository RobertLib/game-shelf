import "./DynamicInputs.css";
import { useState } from "react";
import Button from "./Button";
import Input from "./Input";
import PropTypes from "prop-types";

export default function DynamicInputs({
  className,
  onChange,
  placeholder = "Enter value",
  style,
  type = "text",
}) {
  const [inputs, setInputs] = useState([""]);

  const handleInputChange = (index, { target }) => {
    const newInputs = [...inputs];
    newInputs[index] = target.value;

    setInputs(newInputs);
    onChange?.(newInputs);
  };

  const handleAddInput = () => {
    const newInputs = [...inputs, ""];

    setInputs(newInputs);
    onChange?.(newInputs);
  };

  const handleRemoveInput = (index) => {
    const newInputs = inputs.filter((_, i) => i !== index);

    setInputs(newInputs);
    onChange?.(newInputs);
  };

  return (
    <div className={className} style={style}>
      {inputs.map((value, index) => (
        <div className="DynamicInputs-row" key={index}>
          <Input
            onChange={(event) => handleInputChange(index, event)}
            placeholder={placeholder}
            type={type}
            value={value}
          />
          {inputs.length > 1 && (
            <Button
              onClick={() => handleRemoveInput(index)}
              size="sm"
              variant="danger"
            >
              Remove
            </Button>
          )}
        </div>
      ))}
      <Button onClick={handleAddInput}>Add Input</Button>
    </div>
  );
}

DynamicInputs.propTypes = {
  className: PropTypes.string,
  onChange: PropTypes.func,
  placeholder: PropTypes.string,
  style: PropTypes.object,
  type: PropTypes.string,
};

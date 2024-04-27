import { Button, Input } from "../../components/ui";
import { jwtDecode } from "jwt-decode";
import { Link, useNavigate } from "react-router-dom";
import { useSession } from "../../contexts/session";
import { useState } from "react";
import apiFetch from "../../utils/apiFetch";
import logger from "../../utils/logger";

export default function Register() {
  const [data, setData] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
  });

  const [isLoading, setIsLoading] = useState(false);

  const [errors, setErrors] = useState(null);

  const { setCurrentUser } = useSession();

  const navigate = useNavigate();

  const handleChange = ({ target: { name, value } }) => {
    setData((prevState) => ({ ...prevState, [name]: value }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    try {
      setIsLoading(true);
      setErrors(null);

      const response = await apiFetch("/api/auth/register", {
        method: "POST",
        body: JSON.stringify(data),
      });

      const result = await response.json();

      if (!response.ok) {
        setErrors(result);
        return;
      }

      logger.info("User created:", result);

      localStorage.setItem("token", result.token);

      setCurrentUser(jwtDecode(result.token));

      navigate("/");
    } catch (error) {
      logger.error("Error creating user:", error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="container container-sm">
      <h1>Register</h1>

      <form
        className="panel"
        onSubmit={handleSubmit}
        style={{ padding: "2rem" }}
      >
        {errors?.errorMessage && (
          <div className="alert alert-danger" style={{ marginBottom: "1rem" }}>
            {errors.errorMessage}
          </div>
        )}

        <div className="col">
          <Input
            autoComplete="email"
            error={errors?.email}
            fullWidth
            label="Email"
            maxLength={255}
            name="email"
            onChange={handleChange}
            required
            type="email"
            value={data.email}
          />

          <Input
            autoComplete="new-password"
            error={errors?.password}
            fullWidth
            label="Password"
            maxLength={255}
            name="password"
            onChange={handleChange}
            required
            type="password"
            value={data.password}
          />

          <Input
            autoComplete="new-password"
            error={errors?.passwordConfirm}
            fullWidth
            label="Confirm Password"
            maxLength={255}
            name="passwordConfirm"
            onChange={handleChange}
            required
            type="password"
            value={data.passwordConfirm}
          />
        </div>

        <Button
          className="w-full"
          loading={isLoading}
          size="lg"
          style={{ marginTop: "1.5rem" }}
          type="submit"
        >
          Register
        </Button>

        <p className="text-center" style={{ marginBottom: 0 }}>
          or,{" "}
          <Link className="link" to="/auth/login">
            login
          </Link>
        </p>
      </form>
    </div>
  );
}

import { Navigate } from "react-router-dom";
import { useSession } from "../../contexts/session";
import PropTypes from "prop-types";

function ProtectedRoute({ children, role }) {
  const { currentUser } = useSession();

  if (!currentUser || (role && currentUser.role !== role)) {
    return <Navigate to="/auth/login" replace />;
  }

  return children;
}

export default ProtectedRoute;

ProtectedRoute.propTypes = {
  children: PropTypes.node.isRequired,
  role: PropTypes.string,
};

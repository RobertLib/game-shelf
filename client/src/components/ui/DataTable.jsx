import "./DataTable.css";
import { useRef } from "react";
import { useSearchParams } from "react-router-dom";
import debounce from "../../utils/debounce";
import Input from "./Input";
import Pagination from "./Pagination";
import PropTypes from "prop-types";
import removeDiacritics from "../../utils/removeDiacritics";
import Select from "./Select";
import Spinner from "./Spinner";

export default function DataTable({
  actions,
  className,
  columns,
  data,
  loading,
  style,
  toolbar,
  total,
}) {
  const [searchParams, setSearchParams] = useSearchParams();

  const filters = JSON.parse(searchParams.get("filters") ?? "{}");
  const sortKey = searchParams.get("sortKey") ?? "";
  const sortOrder = searchParams.get("sortOrder") ?? "asc";
  const page = parseInt(searchParams.get("page") ?? "1", 10);
  const limit = parseInt(searchParams.get("limit") ?? "20", 10);

  const handleSort = (key) => {
    let order = "asc";

    if (sortKey === key && sortOrder === "asc") {
      order = "desc";
    }

    searchParams.set("sortKey", key);
    searchParams.set("sortOrder", order);

    setSearchParams(searchParams);
  };

  const highlightText = (text, searchValue) => {
    if (!searchValue) return text;

    const regex = new RegExp(`(${removeDiacritics(searchValue)})`, "gi");
    return text.replace(regex, "<b>$1</b>");
  };

  const toolbarRef = useRef(null);

  return (
    <div className={className} style={style}>
      <div className="table-container">
        {toolbar && (
          <div className="DataTable-toolbar" ref={toolbarRef}>
            {toolbar}
          </div>
        )}
        <table className="table">
          <thead
            className="DataTable-head"
            style={{ top: toolbarRef.current?.clientHeight }}
          >
            <tr>
              {actions && <th className="DataTable-actions">Actions</th>}
              {columns.map((column) => (
                <th key={column.key}>
                  <div className="DataTable-head-cell">
                    {column.sortable ? (
                      <button
                        className="DataTable-sort-btn btn font-bold text-sm"
                        onClick={() => handleSort(column.key)}
                        type="button"
                      >
                        {column.label}
                        {sortKey === column.key && (
                          <span>{sortOrder === "asc" ? "▲" : "▼"}</span>
                        )}
                      </button>
                    ) : (
                      <span className="font-bold text-sm">{column.label}</span>
                    )}
                    {column.filter === "input" && (
                      <Input
                        defaultValue={filters[column.key] ?? ""}
                        dim="sm"
                        onChange={debounce(({ target: { value } }) => {
                          filters[column.key] = value;

                          searchParams.set("filters", JSON.stringify(filters));
                          searchParams.set("page", "1");

                          setSearchParams(searchParams);
                        }, 500)}
                        placeholder={`Search ${column.label}`}
                        type="search"
                      />
                    )}
                    {column.filter === "select" && (
                      <Select
                        defaultValue={filters[column.key] ?? ""}
                        dim="sm"
                        onChange={({ target: { value } }) => {
                          filters[column.key] = value;

                          searchParams.set("filters", JSON.stringify(filters));
                          searchParams.set("page", "1");

                          setSearchParams(searchParams);
                        }}
                        options={column.filterSelectOptions ?? []}
                      />
                    )}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {(data?.length ?? 0) === 0 ? (
              <tr>
                <td colSpan={columns.length + (actions ? 1 : 0)}>
                  {loading ? (
                    <Spinner />
                  ) : (
                    <p className="text-center">No data available</p>
                  )}
                </td>
              </tr>
            ) : (
              data?.map((row) => (
                <tr key={row.id}>
                  {actions && (
                    <td className="DataTable-actions">{actions(row)}</td>
                  )}
                  {columns.map((column) => {
                    const cellContent = column.render?.(row) ?? row[column.key];

                    if (
                      typeof cellContent === "string" ||
                      typeof cellContent === "number"
                    ) {
                      const highlightedContent = highlightText(
                        String(cellContent),
                        filters[column.key] ?? ""
                      );

                      return (
                        <td
                          dangerouslySetInnerHTML={{
                            __html: highlightedContent,
                          }}
                          key={column.key}
                        />
                      );
                    }

                    return <td key={column.key}>{cellContent}</td>;
                  })}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <div className="DataTable-footer">
        <Select
          dim="sm"
          onChange={({ target: { value } }) => {
            searchParams.set("limit", value);
            setSearchParams(searchParams);
          }}
          options={[5, 10, 15, 20, 25, 30, 50, 100].map((value) => ({
            label: value.toString(),
            value,
          }))}
          value={limit}
        />

        <Pagination
          limit={limit}
          onChange={(page) => {
            searchParams.set("page", String(page));
            setSearchParams(searchParams);
          }}
          page={page}
          total={total}
        />
      </div>
    </div>
  );
}

export const useDataTable = () => {
  const [searchParams] = useSearchParams();

  const filters = JSON.parse(searchParams.get("filters") ?? "{}");
  const sortKey = searchParams.get("sortKey") ?? "";
  const sortOrder = searchParams.get("sortOrder") ?? "asc";
  const page = parseInt(searchParams.get("page") ?? "1", 10);
  const limit = parseInt(searchParams.get("limit") ?? "20", 10);

  return {
    ...filters,
    sortKey,
    sortOrder,
    limit: limit.toString(),
    offset: String((page - 1) * limit),
  };
};

DataTable.propTypes = {
  actions: PropTypes.func,
  className: PropTypes.string,
  columns: PropTypes.arrayOf(
    PropTypes.shape({
      key: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired,
      render: PropTypes.func,
      filter: PropTypes.oneOf(["input", "select"]),
      filterSelectOptions: PropTypes.arrayOf(
        PropTypes.shape({
          label: PropTypes.string.isRequired,
          value: PropTypes.string.isRequired,
        })
      ),
      sortable: PropTypes.bool,
    })
  ).isRequired,
  data: PropTypes.array,
  loading: PropTypes.bool,
  style: PropTypes.object,
  toolbar: PropTypes.node,
  total: PropTypes.number,
};

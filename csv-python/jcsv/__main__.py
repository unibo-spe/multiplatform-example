import argparse

import jcsv


def main():
    parser = argparse.ArgumentParser(
        description="Echoes CSV file to stdout.", prog="python -m jcsv"
    )
    parser.add_argument("path", metavar="PATH", type=str, help="path of the CSV file to echo")
    parser.add_argument(
        "-s",
        "--separator",
        metavar="SEP",
        default=jcsv.Csv.DEFAULT_SEPARATOR,
        help="CSV separator char",
    )
    parser.add_argument(
        "-d",
        "--delimiter",
        metavar="DEL",
        default=jcsv.Csv.DEFAULT_DELIMITER,
        help="CSV delimiter char",
    )
    parser.add_argument(
        "-c", "--comment", metavar="COM", default=jcsv.Csv.DEFAULT_COMMENT, help="CSV comment char"
    )
    args = parser.parse_args()
    table = jcsv.parse_csv_file(args.path, args.separator, args.delimiter, args.comment)
    print(jcsv.format_as_csv(table, args.separator, args.delimiter, args.comment))


if __name__ == "__main__":
    main()
